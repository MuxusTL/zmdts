import re
with open("app/src/main/java/dev/nitroplus/ui/FloatingMenuService.java", "r") as f:
    content = f.read()

# Wrap onCreate content in try-catch
old_onCreate = """    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);"""

new_onCreate = """    public void onCreate() {
        super.onCreate();
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);"""

content = content.replace(old_onCreate, new_onCreate)

old_endCreate = """        windowManager.addView(logoView, logoParams);
        windowManager.addView(menuView, menuParams);
    }"""

new_endCreate = """        windowManager.addView(logoView, logoParams);
        windowManager.addView(menuView, menuParams);
        } catch (Exception e) {
            android.util.Log.e("NitroVPN", "Menu Error: " + android.util.Log.getStackTraceString(e));
            Toast.makeText(this, "Lỗi hiển thị Menu: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }"""

content = content.replace(old_endCreate, new_endCreate)

with open("app/src/main/java/dev/nitroplus/ui/FloatingMenuService.java", "w") as f:
    f.write(content)
