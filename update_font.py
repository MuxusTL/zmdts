import re

with open("app/src/main/java/dev/nitroplus/ui/FloatingMenuService.java", "r") as f:
    content = f.read()

# Load interBlack
load_fonts = """        Typeface spaceGrotesk = Typeface.createFromAsset(getAssets(), "fonts/space_grotesk.ttf");"""
new_load_fonts = load_fonts + """\n        Typeface interBlack = Typeface.createFromAsset(getAssets(), "fonts/inter_black.ttf");"""

content = content.replace(load_fonts, new_load_fonts)

# Replace button1 typeface
old_btn = """        TextView button1 = menuView.findViewById(R.id.button1);
        if(button1 != null) button1.setTypeface(inter, android.graphics.Typeface.BOLD);"""
new_btn = """        TextView button1 = menuView.findViewById(R.id.button1);
        if(button1 != null) button1.setTypeface(interBlack, android.graphics.Typeface.NORMAL);"""

content = content.replace(old_btn, new_btn)

with open("app/src/main/java/dev/nitroplus/ui/FloatingMenuService.java", "w") as f:
    f.write(content)
