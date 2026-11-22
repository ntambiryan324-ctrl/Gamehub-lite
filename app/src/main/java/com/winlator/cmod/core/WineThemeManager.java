package com.winlator.cmod.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.winlator.cmod.R;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xserver.ScreenInfo;

import java.io.File;

public abstract class WineThemeManager {
    public enum Theme {LIGHT, DARK}
    public enum BackgroundType {IMAGE, COLOR}
    public static final String DEFAULT_DESKTOP_THEME = Theme.LIGHT+","+BackgroundType.IMAGE+",#0277bd";

    public static class ThemeInfo {
        public final Theme theme;
        public final BackgroundType backgroundType;
        public final int backgroundColor;

        public ThemeInfo(String value) {
            String[] values = value.split(",");
            theme = Theme.valueOf(values[0]);
            if (values.length < 3) {
                backgroundColor = Color.parseColor(values[1]);
                backgroundType = BackgroundType.IMAGE;
            }
            else {
                backgroundType = BackgroundType.valueOf(values[1]);
                backgroundColor = Color.parseColor(values[2]);
            }
        }
    }

    public static void apply(Context context, ThemeInfo themeInfo, ScreenInfo screenInfo) {
        File rootDir = ImageFs.find(context).getRootDir();
        File userRegFile = new File(rootDir, ImageFs.WINEPREFIX+"/user.reg");
        String background = Color.red(themeInfo.backgroundColor)+" "+Color.green(themeInfo.backgroundColor)+" "+Color.blue(themeInfo.backgroundColor);

        if (themeInfo.backgroundType == BackgroundType.IMAGE) createWallpaperBMPFile(context, screenInfo);

        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            if (themeInfo.backgroundType == BackgroundType.IMAGE) {
                registryEditor.setStringValue("Control Panel\\Desktop", "Wallpaper", ImageFs.CACHE_PATH+"/wallpaper.bmp");
            }
            else registryEditor.removeValue("Control Panel\\Desktop", "Wallpaper");

            if (themeInfo.theme == Theme.LIGHT) {
                registryEditor.setStringValue("Control Panel\\Colors", "ActiveBorder", "245 245 245");
                registryEditor.setStringValue("Control Panel\\Colors", "ActiveTitle", "96 125 139");
                registryEditor.setStringValue("Control Panel\\Colors", "Background", background);
                registryEditor.setStringValue("Control Panel\\Colors", "ButtonAlternateFace", "245 245 245");
                registryEditor.setStringValue("Control Panel\\Colors", "ButtonDkShadow", "158 158 158");
                registryEditor.setStringValue("Control Panel\\Colors", "ButtonFace", "245 245 245");
                registryEditor.setStringValue("Control Panel\\Colors", "ButtonHilight", "224 224 224");
                registryEditor.setStringValue("Control Panel\\Colors", "ButtonLight", "255 255 255");
                registryEditor.setStringValue("Control Panel\\Colors", "ButtonShadow", "158 158 158");
                registryEditor.setStringValue("Control Panel\\Colors", "ButtonText", "0 0 0");
                registryEditor.setStringValue("Control Panel\\Colors", "GradientActiveTitle", "96 125 139");
                registryEditor.setStringValue("Control Panel\\Colors", "GradientInactiveTitle", "117 117 117");
                registryEditor.setStringValue("Control Panel\\Colors", "GrayText", "158 158 158");
                registryEditor.setStringValue("Control Panel\\Colors", "Hilight", "2 136 209");
                registryEditor.setStringValue("Control Panel\\Colors", "HilightText", "255 255 255");
                registryEditor.setStringValue("Control Panel\\Colors", "HotTrackingColor", "2 136 209");
                registryEditor.setStringValue("Control Panel\\Colors", "InactiveBorder", "255 255 255");
                registryEditor.setStringValue("Control Panel\\Colors", "InactiveTitle", "117 117 117");
                registryEditor.setStringValue("Control Panel\\Colors", "InactiveTitleText", "200 200 200");
                registryEditor.setStringValue("Control Panel\\Colors", "InfoText", "0 0 0");
                registryEditor.setStringValue("Control Panel\\Colors", "InfoWindow", "255 255 255");
                registryEditor.setStringValue("Control Panel\\Colors", "Menu", "245 245 245");
                registryEditor.setStringValue("Control Panel\\Colors", "MenuBar", "245 245 245");
                registryEditor.setStringValue("Control Panel\\Colors", "MenuHilight", "2 136 209");
                registryEditor.setStringValue("Control Panel\\Colors", "MenuText", "0 0 0");
                registryEditor.setStringValue("Control Panel\\Colors", "Scrollbar", "245 245 245");
                registryEditor.setStringValue("Control Panel\\Colors", "TitleText", "255 255 255");
                registryEditor.setStringValue("Control Panel\\Colors", "Window", "245 245 245");
                registryEditor.setStringValue("Control Panel\\Colors", "WindowFrame", "158 158 158");
                registryEditor.setStringValue("Control Panel\\Colors", "WindowText", "0 0 0");
            }
            else if (themeInfo.theme == Theme.DARK) {
                registryEditor.setStringValue("Control Panel\\Colors", "ActiveBorder", "48 48 48");
                registryEditor.setStringValue("Control Panel\\Colors", "ActiveTitle", "33 33 33");
                registryEditor.setStringValue("Control Panel\\Colors", "Background", background);
                registryEditor.setStringValue("Control Panel\\Colors", "ButtonAlternateFace", "33 33 33");
                registryEditor.setStringValue("Control Panel\\Colors", "ButtonDkShadow", "0 0 0");
                registryEditor.setStringValue("Control Panel\\Colors", "ButtonFace", "33 33 33");
                registryEditor.setStringValue("Control Panel\\Colors", "ButtonHilight", "48 48 48");
                registryEditor.setStringValue("Control Panel\\Colors", "ButtonLight", "48 48 48");
                registryEditor.setStringValue("Control Panel\\Colors", "ButtonShadow", "0 0 0");
                registryEditor.setStringValue("Control Panel\\Colors", "ButtonText", "255 255 255");
                registryEditor.setStringValue("Control Panel\\Colors", "GradientActiveTitle", "33 33 33");
                registryEditor.setStringValue("Control Panel\\Colors", "GradientInactiveTitle", "33 33 33");
                registryEditor.setStringValue("Control Panel\\Colors", "GrayText", "117 117 117");
                registryEditor.setStringValue("Control Panel\\Colors", "Hilight", "2 136 209");
                registryEditor.setStringValue("Control Panel\\Colors", "HilightText", "255 255 255");
                registryEditor.setStringValue("Control Panel\\Colors", "HotTrackingColor", "2 136 209");
                registryEditor.setStringValue("Control Panel\\Colors", "InactiveBorder", "48 48 48");
                registryEditor.setStringValue("Control Panel\\Colors", "InactiveTitle", "33 33 33");
                registryEditor.setStringValue("Control Panel\\Colors", "InactiveTitleText", "117 117 117");
                registryEditor.setStringValue("Control Panel\\Colors", "InfoText", "255 255 255");
                registryEditor.setStringValue("Control Panel\\Colors", "InfoWindow", "255 255 255");
                registryEditor.setStringValue("Control Panel\\Colors", "Menu", "33 33 33");
                registryEditor.setStringValue("Control Panel\\Colors", "MenuBar", "48 48 48");
                registryEditor.setStringValue("Control Panel\\Colors", "MenuHilight", "2 136 209");
                registryEditor.setStringValue("Control Panel\\Colors", "MenuText", "255 255 255");
                registryEditor.setStringValue("Control Panel\\Colors", "Scrollbar", "48 48 48");
                registryEditor.setStringValue("Control Panel\\Colors", "TitleText", "255 255 255");
                registryEditor.setStringValue("Control Panel\\Colors", "Window", "48 48 48");
                registryEditor.setStringValue("Control Panel\\Colors", "WindowFrame", "0 0 0");
                registryEditor.setStringValue("Control Panel\\Colors", "WindowText", "255 255 255");
            }
        }
    }

    private static void createWallpaperBMPFile(Context context, ScreenInfo screenInfo) {
        int w = Math.max(1, screenInfo.width);
        int h = Math.max(1, screenInfo.height);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        File customFile = getUserWallpaperFile(context);
        if (customFile.isFile()) {
            Bitmap custom = android.graphics.BitmapFactory.decodeFile(customFile.getAbsolutePath());
            if (custom != null) {
                canvas.drawBitmap(custom, null, new android.graphics.Rect(0, 0, w, h),
                        new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
                custom.recycle();
            } else drawPocketWinBloom(context, canvas, w, h);
        } else drawPocketWinBloom(context, canvas, w, h);

        ImageFs imageFs = ImageFs.find(context);
        MSBitmap.create(bitmap, new File(imageFs.getRootDir(), ImageFs.CACHE_PATH+"/wallpaper.bmp"));
        bitmap.recycle();
    }

    private static void drawPocketWinBloom(Context context, Canvas canvas, int width, int height) {
        Bitmap wallpaper = android.graphics.BitmapFactory.decodeResource(
                context.getResources(), R.drawable.pocketwin_wallpaper);
        if (wallpaper == null) {
            Paint fallback = new Paint(Paint.ANTI_ALIAS_FLAG);
            fallback.setColor(0xFF07172F);
            canvas.drawRect(0, 0, width, height, fallback);
            return;
        }
        float sourceRatio = wallpaper.getWidth() / (float) wallpaper.getHeight();
        float targetRatio = width / (float) height;
        android.graphics.Rect source;
        if (sourceRatio > targetRatio) {
            int cropWidth = Math.max(1, (int) (wallpaper.getHeight() * targetRatio));
            int left = (wallpaper.getWidth() - cropWidth) / 2;
            source = new android.graphics.Rect(left, 0, left + cropWidth, wallpaper.getHeight());
        } else {
            int cropHeight = Math.max(1, (int) (wallpaper.getWidth() / targetRatio));
            int top = (wallpaper.getHeight() - cropHeight) / 2;
            source = new android.graphics.Rect(0, top, wallpaper.getWidth(), top + cropHeight);
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(wallpaper, source, new android.graphics.Rect(0, 0, width, height), paint);
        wallpaper.recycle();
    }

    public static File getUserWallpaperFile(Context context) {
        return new File(ImageFs.find(context).getRootDir(), ImageFs.CONFIG_PATH+"/user-wallpaper.png");
    }
}
