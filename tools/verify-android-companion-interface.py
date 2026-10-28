#!/usr/bin/env python3
"""Guard Android companion interface discovery against emulator/VPN interface regressions."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "apps/android/app/src/main/java/ph/kanta/reborn/CompanionControl.java").read_text()
start = source.index("static List<String> addresses()")
end = source.index("\n  }\n}", start)
addresses = source[start:end]

assert "if(!network.isUp()||network.isLoopback())continue;" in addresses, (
    "Interface discovery must skip down and loopback interfaces"
)
assert "network.isVirtual()" not in addresses, (
    "Do not reject a private IPv4 interface only because Android reports it as virtual"
)
assert "CompanionProtocol.privateAddress(address.getHostAddress())" in addresses, (
    "Every candidate address must pass the strict RFC1918 IPv4 validator"
)
assert "endpoint=InetAddress.getByAddress(CompanionProtocol.privateAddress(address));" in source, (
    "Hosting must bind only to the explicitly selected private IPv4 address"
)
assert "NetworkInterface.getByInetAddress(endpoint)==null" in source, (
    "Hosting must confirm the selected address belongs to a local interface"
)
assert "server.bind(new InetSocketAddress(endpoint,PORT),1)" in source, (
    "Hosting must bind to the selected interface, never a wildcard address"
)
print("Android companion interface discovery contract passed")
