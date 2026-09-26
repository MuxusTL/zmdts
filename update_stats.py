import re

with open("app/src/main/java/dev/nitroplus/ui/FloatingMenuService.java", "r") as f:
    content = f.read()

# Add DomainStore import
if "import dev.nitroplus.data.DomainStore;" not in content:
    content = content.replace("import dev.nitroplus.R;", "import dev.nitroplus.R;\nimport dev.nitroplus.data.DomainStore;")

# Add updateStats method
stats_logic = """
    private void updateStats() {
        DomainStore domainStore = DomainStore.getInstance(this);
        if (domainStore != null) {
            TextView blockedcount = menuView.findViewById(R.id.blockedcount);
            TextView activedomaincount = menuView.findViewById(R.id.activedomaincount);
            if (blockedcount != null) {
                blockedcount.setText(String.valueOf(domainStore.getBlockedCount()));
            }
            if (activedomaincount != null) {
                activedomaincount.setText(String.valueOf(domainStore.getEnabledCount()));
            }
        }
    }
"""
content = content.replace("private void updateVpnUI(boolean isRunning) {", stats_logic + "\n    private void updateVpnUI(boolean isRunning) {")

# Call updateStats in setupMenu
content = content.replace("updateVpnUI(dev.nitroplus.vpn.VpnService.isRunning);", "updateVpnUI(dev.nitroplus.vpn.VpnService.isRunning);\n        updateStats();")

# Call updateStats when opening menu
content = content.replace("menuView.setVisibility(isMenuOpen ? View.VISIBLE : View.GONE);", "if (isMenuOpen) updateStats();\n                            menuView.setVisibility(isMenuOpen ? View.VISIBLE : View.GONE);")

with open("app/src/main/java/dev/nitroplus/ui/FloatingMenuService.java", "w") as f:
    f.write(content)
