package xyz.angm.game.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import xyz.angm.game.Game;
import xyz.angm.game.network.Client;
import xyz.angm.game.ui.PausePanel;
import xyz.angm.game.ui.PlayerHud;
import xyz.angm.game.ui.SpectatorHud;
import xyz.angm.game.ui.input.InputProcessor;
import xyz.angm.game.ui.input.PlayerInputProcessor;
import xyz.angm.game.ui.input.SpectatorInputProcessor;
import xyz.angm.game.world.TileVector;
import xyz.angm.game.world.World;
import xyz.angm.game.world.blocks.Block;
import xyz.angm.game.world.entities.Player;

/** The screen active while the game is running. */
public class GameScreen extends Screen {

    private boolean pauseMenuActive = false;
    private World world;
    private PlayerHud hud;
    private final InputMultiplexer inputMultiplexer = new InputMultiplexer();
    private final Vector2 tmpV = new Vector2();

    /** Constructs the screen. Automatically determines if player or spectator.
     * @param game The game the screen is running under.
     * @param world The world to use. */
    GameScreen(Game game, World world) {
        super(game);
        this.world = world;
        if (game.isServer()) initServer();
        else initClient();
        stage.addActor(hud);
    }

    private void initServer() {
        hud = new PlayerHud(this);
        initInput(new PlayerInputProcessor(this));
    }

    private void initClient() {
        game.getClient().addListener(this::serverPacketReceived);
        world.freeCamera();
        hud = new SpectatorHud(this);
        initInput(new SpectatorInputProcessor(this));
    }

    // Create a multiplexer for handling input for both UI and in-world (https://github.com/libgdx/libgdx/wiki/Event-handling#inputmultiplexer)
    private void initInput(InputProcessor processor) {
        inputMultiplexer.addProcessor(stage);
        inputMultiplexer.addProcessor(processor);
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    public World getWorld() {
        return world;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        world.act(delta);
        world.render();     // World render is separate to allow for different camera positions.

        stage.act(delta);
        stage.draw();
    }

    // Called when a packet/object was received from the server. Only call on client instances.
    private void serverPacketReceived(Object packet) {
        if (packet instanceof Player) {  // Player should be synced
            Player serverPlayer = (Player) packet;
            Player localPlayer = world.getPlayer();
            localPlayer.getPosition().set(serverPlayer.getPosition());
        }
        else if (packet == Client.Status.DISCONNECTED) { // Disconnect from server
            Gdx.app.postRunnable(this::returnToMainMenu); // Disposing game screen requires render context
        }
        else if (packet instanceof Block) { // Block should be placed
            world.addBlock((Block) packet);
        }
        else if (packet instanceof TileVector) { // Block should removed
            world.removeBlock((TileVector) packet);
        }
    }

    /** Should be called when the player clicked the screen. Will place or break a block at the clicked position and sync to clients.
     * @see World
     * @param x The x position of the click in screen coordinates.
     * @param y The y position of the click in screen coordinates.
     * @param rightClick If the click was a right click. Left click assumed if false. */
    public void mapClicked(int x, int y, boolean rightClick) {
        tmpV.set(x, y);
        world.screenToWorldCoordinates(tmpV);
        TileVector position = new TileVector().set(tmpV);
        Block block = world.mapClicked(position, rightClick);

        // Sync with clients
        if (block == null) game.getServer().send(position);
        else game.getServer().send(block);
    }

    /** Toggles the pause menu. */
    public void togglePausePanel() {
        stage.clear();
        if (!pauseMenuActive) { // Open the pause menu
            stage.addActor(new PausePanel(this));
            Gdx.input.setInputProcessor(stage);
        } else { // Close the pause menu
            stage.addActor(hud);
            Gdx.input.setInputProcessor(inputMultiplexer);
        }
        pauseMenuActive = !pauseMenuActive;
    }

    /** Go back to the main menu. */
    public void returnToMainMenu() {
        dispose();
        game.setScreen(new MenuScreen(game));
    }

    /** Called when the locale changed. Reloads all UI. */
    public void localeChanged() {
        stage.clear();
        hud.reload();
        if (pauseMenuActive) stage.addActor(new PausePanel(this));
        else stage.addActor(hud);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (world != null) world.resizeViewport(width, height);
    }

    @Override
    public void dispose() {
        super.dispose();
        world.dispose();
        game.disposeNetworkInterface();
    }
}
