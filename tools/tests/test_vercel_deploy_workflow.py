"""Regression checks for the non-interactive KANTA Vercel deployment contract."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/vercel-deploy.yml"


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

    def test_production_deploys_are_serialized(self):
        self.assertIn("group: vercel-deploy-${{ github.event.pull_request.head.ref || github.ref }}", self.source)
        self.assertIn("cancel-in-progress: false", self.source)


if __name__ == "__main__":
    unittest.main()
