import re

with open("app/src/main/java/dev/nitroplus/ui/FloatingMenuService.java", "r") as f:
    content = f.read()

# Add font loading code after setupMenu()
font_logic = """        // Setup fonts
        android.graphics.Typeface spaceGrotesk = android.graphics.Typeface.createFromAsset(getAssets(), "fonts/space_grotesk.ttf");
        android.graphics.Typeface inter = android.graphics.Typeface.createFromAsset(getAssets(), "fonts/inter.ttf");
        android.graphics.Typeface jbMono = android.graphics.Typeface.createFromAsset(getAssets(), "fonts/jb_mono.ttf");
        
        TextView textview1 = menuView.findViewById(R.id.textview1);
        if(textview1 != null) textview1.setTypeface(spaceGrotesk, android.graphics.Typeface.BOLD);
        
        TextView textview2 = menuView.findViewById(R.id.textview2);
        if(textview2 != null) textview2.setTypeface(inter, android.graphics.Typeface.NORMAL);
        
        TextView textview3 = menuView.findViewById(R.id.textview3);
        if(textview3 != null) textview3.setTypeface(inter, android.graphics.Typeface.NORMAL);
        
        TextView textview4 = menuView.findViewById(R.id.textview4);
        if(textview4 != null) textview4.setTypeface(inter, android.graphics.Typeface.NORMAL);
        
        TextView button1 = menuView.findViewById(R.id.button1);
        if(button1 != null) button1.setTypeface(inter, android.graphics.Typeface.BOLD);
        
        TextView blockedcount = menuView.findViewById(R.id.blockedcount);
        if(blockedcount != null) blockedcount.setTypeface(jbMono, android.graphics.Typeface.NORMAL);
        
        TextView activedomaincount = menuView.findViewById(R.id.activedomaincount);
        if(activedomaincount != null) activedomaincount.setTypeface(jbMono, android.graphics.Typeface.NORMAL);
        
        if(tvVpnStatus != null) tvVpnStatus.setTypeface(spaceGrotesk, android.graphics.Typeface.BOLD);
"""

# Inject into setupMenu
content = content.replace("btnClose.setOnClickListener(v -> stopSelf());", "btnClose.setOnClickListener(v -> stopSelf());\n" + font_logic)

with open("app/src/main/java/dev/nitroplus/ui/FloatingMenuService.java", "w") as f:
    f.write(content)
