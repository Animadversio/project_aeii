package com.toyknight.aeii;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;

import java.io.UnsupportedEncodingException;

/**
 * Created by toyknight on 4/2/2015.
 */
public class FontRenderer {

    private static BitmapFont font_yahei;

    private static BitmapFont current_font;

    private FontRenderer() {
    }

    public static void loadFonts() {
        //FileHandle font_yahei_file = Gdx.files.internal("assets/fonts/ms_yahei.fnt");
        font_yahei = new BitmapFont(Gdx.files.internal("assets/fonts/ms_yahei.fnt"), Gdx.files.internal("assets/fonts/ms_yahei.png"), false);

        current_font = font_yahei;
    }

    public static void setFont(String font) {
        if(font.equals("YaHei")) {
            current_font = font_yahei;
        }
    }

    public static void setColor(float r, float g, float b, float a) {
        current_font.setColor(r, g, b, a);
    }

    public static void drawString(Batch batch, String str, float x, float y) {
        current_font.draw(batch, str, x, y);
    }


}
