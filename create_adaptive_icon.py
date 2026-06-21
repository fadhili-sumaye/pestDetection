from PIL import Image, ImageOps
import os

# Paths
foreground_src = r'C:\Users\fadhi\.gemini\antigravity\brain\0bc9ee3b-68d8-41f2-a1e9-068786fe9bd8\pest_scanner_foreground_1777748069318.png'
background_src = r'C:\Users\fadhi\.gemini\antigravity\brain\0bc9ee3b-68d8-41f2-a1e9-068786fe9bd8\pest_detector_bg_1777748386747.png'
res_path = r'c:\Users\fadhi\StudioProjects\pestDetection\app\src\main\res'

def make_transparent(img_path):
    img = Image.open(img_path).convert("RGBA")
    datas = img.getdata()
    newData = []
    for item in datas:
        # If it's very dark (black), make it transparent
        if item[0] < 30 and item[1] < 30 and item[2] < 30:
            newData.append((255, 255, 255, 0))
        else:
            newData.append(item)
    img.putdata(newData)
    return img

def create_adaptive_icon():
    # Process Foreground (remove black)
    fg_img = make_transparent(foreground_src)
    bg_img = Image.open(background_src).convert("RGBA")

    # Standard Adaptive Icon size is 108x108dp. 
    # For xxxhdpi (4x), it is 432x432px.
    # We will save high-res versions and let Android scale them.
    
    # Ensure drawable folders exist
    os.makedirs(os.path.join(res_path, 'drawable'), exist_ok=True)
    os.makedirs(os.path.join(res_path, 'mipmap-anydpi-v26'), exist_ok=True)

    # Save layers to drawable
    fg_img.resize((512, 512), Image.Resampling.LANCZOS).save(os.path.join(res_path, 'drawable', 'ic_launcher_foreground.webp'), 'WEBP')
    bg_img.resize((512, 512), Image.Resampling.LANCZOS).save(os.path.join(res_path, 'drawable', 'ic_launcher_background.webp'), 'WEBP')

    # Create the XML
    xml_content = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>"""

    with open(os.path.join(res_path, 'mipmap-anydpi-v26', 'ic_launcher.xml'), 'w') as f:
        f.write(xml_content)
    with open(os.path.join(res_path, 'mipmap-anydpi-v26', 'ic_launcher_round.xml'), 'w') as f:
        f.write(xml_content)

    print("Adaptive icons created successfully!")

if __name__ == '__main__':
    create_adaptive_icon()
