package xyz.angm.game.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

/** An input processor for handling inputs by both player and spectator. Does not handle UI. */
class InputProcessor extends InputAdapter {

    private static final float SCROLL_SCALING = 0.01f;

    private final GameScreen screen;

    /** Create an input processor.
     * @param screen The screen to bind to */
    InputProcessor(GameScreen screen) {
        this.screen = screen;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) { // Pause Menu
            screen.togglePausePanel();
        }
        return true;
    }

    @Override
    public boolean scrolled(int amount) {
        float scrolled = (float) amount * SCROLL_SCALING;
        screen.getWorld().zoomMap(scrolled);
        return true;
    }
}
