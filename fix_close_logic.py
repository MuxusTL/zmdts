import re

with open("app/src/main/java/dev/nitroplus/ui/FloatingMenuService.java", "r") as f:
    content = f.read()

old_close_logic = """btnClose.setOnClickListener(v -> stopSelf());
        TextView btnCloseMenu = menuView.findViewById(R.id.button1);
        if (btnCloseMenu != null) btnCloseMenu.setOnClickListener(v -> stopSelf());"""

new_close_logic = """btnClose.setOnClickListener(v -> stopSelf());
        TextView btnCloseMenu = menuView.findViewById(R.id.button1);
        if (btnCloseMenu != null) {
            btnCloseMenu.setOnClickListener(v -> {
                isMenuOpen = false;
                menuView.setVisibility(View.GONE);
            });
        }"""

content = content.replace(old_close_logic, new_close_logic)

with open("app/src/main/java/dev/nitroplus/ui/FloatingMenuService.java", "w") as f:
    f.write(content)
