"""Regression checks for the non-interactive KANTA Vercel deployment contract."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/vercel-deploy.yml"
DOMAIN_WORKFLOW = ROOT / ".github/workflows/configure-is-a-dev-domain.yml"


class VercelDeployWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = WORKFLOW.read_text(encoding="utf-8")

    def test_targets_the_existing_team_and_project(self):
        self.assertIn("VERCEL_ORG_ID: team_6rrG5QXBI00wEFufnjKSJLWj", self.source)
        self.assertIn("VERCEL_PROJECT_ID: prj_OG4AuuRqXOY9EAPRJuSvnT3UXvA6", self.source)
        self.assertIn("VERCEL_TOKEN: ${{ secrets.VERCEL_TOKEN }}", self.source)

    def test_cli_version_is_pinned(self):
        self.assertIn('VERCEL_CLI_VERSION: "65.2.0"', self.source)
        self.assertIn('npm install --global "vercel@${VERCEL_CLI_VERSION}"', self.source)
        self.assertNotIn("vercel@latest", self.source)

    def test_pull_build_and_prebuilt_deploy_are_noninteractive(self):
        self.assertIn('vercel pull --yes --environment=production --token="$VERCEL_TOKEN"', self.source)
        self.assertIn('vercel build --prod --token="$VERCEL_TOKEN"', self.source)
        self.assertIn('vercel deploy --prebuilt --prod --yes --token="$VERCEL_TOKEN"', self.source)
        self.assertNotIn("vercel deploy --prod --token=", self.source)

    def test_cli_runs_from_repository_root_for_configured_project_root(self):
        self.assertNotIn("working-directory: apps/web", self.source)
        self.assertIn("the Vercel project's configured Root Directory is apps/web", self.source)
        self.assertIn("apps/web/apps/web", self.source)

    def test_validation_gates_deployment_and_prs_never_deploy(self):
        self.assertIn("needs: validate", self.source)
        self.assertIn("github.event_name != 'pull_request'", self.source)
        self.assertIn("github.ref == 'refs/heads/main'", self.source)
        self.assertIn("inputs.configure_domain != ''", self.source)
        self.assertIn("pull_request:", self.source)

    def test_push_deploys_do_not_depend_on_how_inputs_resolves(self):
        """On a push event the `inputs` context is null, not an empty string.

        So `inputs.configure_domain != ''` alone evaluates true on a normal release, which
        would run the domain step with an empty value and fail the job, or skip the deploy
        steps entirely. Every gate therefore names the event as well.
        """
        self.assertIn(
            "github.event_name == 'workflow_dispatch' && inputs.configure_domain != ''",
            self.source,
        )
        self.assertEqual(
            self.source.count(
                "github.event_name != 'workflow_dispatch' || inputs.configure_domain == ''"
            ),
            3,
            "the pull, build and deploy steps must all still run on a push event",
        )
        # The bare forms are the bug; they must not come back.
        self.assertNotIn("if: ${{ inputs.configure_domain == '' }}", self.source)
        self.assertNotIn("if: ${{ inputs.configure_domain != '' }}", self.source)

    def test_production_deploys_are_serialized(self):
        self.assertIn("group: vercel-deploy-${{ github.event.pull_request.head.ref || github.ref }}", self.source)
        self.assertIn("cancel-in-progress: false", self.source)


class VercelDomainConfigurationTests(unittest.TestCase):
    """The domain workflow must never pin a hostname.

    The production domain is still being registered, so a hardcoded value goes stale the
    moment the final name changes. The domain is an operator input instead.
    """

    @classmethod
    def setUpClass(cls):
        cls.source = DOMAIN_WORKFLOW.read_text(encoding="utf-8")

    def test_domain_comes_from_an_input_not_a_constant(self):
        self.assertIn("domain:", self.source)
        self.assertIn("required: true", self.source)
        self.assertIn("DOMAIN: ${{ inputs.domain }}", self.source)
        self.assertIn('test -n "${DOMAIN}"', self.source)

    def test_no_hostname_is_hardcoded(self):
        # A literal assignment like --data '{"name":"kanta.is-a.dev"}' must not return.
        self.assertNotIn('"name":"kanta.is-a.', self.source)
        self.assertNotIn('"name":"kanta.is-a.bot"', self.source)
        for line in self.source.splitlines():
            stripped = line.strip()
            if stripped.startswith("#"):
                continue
            self.assertNotIn("is-a.dev", stripped, f"hardcoded domain in: {stripped}")

    def test_domain_is_validated_before_it_reaches_the_request(self):
        self.assertIn("Invalid domain", self.source)
        # Raw string: the workflow emits escaped quotes inside the JSON body.
        self.assertIn(r'--data "{\"name\":\"${DOMAIN}\"}"', self.source)

    def test_only_explicit_dispatch_can_run_it(self):
        self.assertIn("workflow_dispatch:", self.source)
        self.assertNotIn("push:", self.source)
        self.assertNotIn("schedule:", self.source)

    def test_domain_is_already_attached_is_treated_as_success(self):
        self.assertIn('"409"', self.source)


if __name__ == "__main__":
    unittest.main()
