package org.com.webbrowser.utils;

import javafx.scene.paint.Color;
import java.util.Random;

public class ColorUtils {
    private static final Random random = new Random();

    public static Color randomPastelColor() {
        return Color.hsb(random.nextDouble() * 360, 0.5 + random.nextDouble() * 0.2, 0.85 + random.nextDouble() * 0.1);
    }
}