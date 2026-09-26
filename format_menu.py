with open("app/src/main/res/layout/floating_menu.xml", "r") as f:
    xml = f.read()

xml = xml.replace('android:padding="20dp"', 'android:padding="12dp"')
xml = xml.replace('android:layout_marginBottom="24dp"', 'android:layout_marginBottom="16dp"')

# Header text
xml = xml.replace('android:textSize="22sp"', 'android:textSize="17sp"\n                android:fontFamily="@font/space_grotesk"')

# Logo size
xml = xml.replace('android:layout_width="48dp"\n                android:layout_height="48dp"', 'android:layout_width="40dp"\n                android:layout_height="40dp"')
xml = xml.replace('app:cardCornerRadius="24dp"', 'app:cardCornerRadius="20dp"')

# VPN Status Card padding
xml = xml.replace('android:padding="20dp"\n                android:gravity="center_vertical"', 'android:padding="16dp"\n                android:gravity="center_vertical"')

# Labels
xml = xml.replace('android:textSize="13sp"', 'android:textSize="11sp"\n                        android:fontFamily="@font/inter"')
xml = xml.replace('android:layout_marginTop="6dp"', 'android:layout_marginTop="2dp"')

with open("app/src/main/res/layout/floating_menu.xml", "w") as f:
    f.write(xml)
