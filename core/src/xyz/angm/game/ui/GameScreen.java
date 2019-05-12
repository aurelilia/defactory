package xyz.angm.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextButton;
import xyz.angm.game.Game;
import xyz.angm.game.network.Client;
import xyz.angm.game.world.Block;
import xyz.angm.game.world.TileVector;
import xyz.angm.game.world.World;
import xyz.angm.game.world.entities.Player;

/** The screen active while the game is running. */
public class GameScreen extends Screen {

    private boolean pauseMenuActive = false ;
    private World world;
    private final PlayerHud playerHud = new PlayerHud(this);
    private final InputMultiplexer inputMultiplexer = new InputMultiplexer();
    private final Vector2 tmpV = new Vector2();

    /** Constructs the screen and generates a new world. Run only when server is active.
     * @param game The game the screen is running under. */
    public GameScreen(Game game) {
        super(game);
        world = new World(System.currentTimeMillis());
        stage.addActor(playerHud);
        initInput(new PlayerInputProcessor(this));
    }

    /** Constructs the screen and waits for the world from the server.
     * @param game The game the screen is running under.
     * @param client The client to wait for events with. Client should not have connected yet! */
    public GameScreen(Game game, Client client) {
        super(game);
        client.addListener(this::serverPacketReceived);

        boolean connected = client.start();
        if (!connected) {
            table.add(new VisLabel(localization.locals.format("noServer"))).row();
            VisTextButton backButton = new VisTextButton(localization.locals.format("backToMain"));
            backButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    returnToMainMenu();
                }
            });
            table.add(backButton).size(BUTTON_WIDTH, BUTTON_HEIGHT);
        }
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

        if (world != null) {        // Waiting for server connect; or display 'no server found' message
            world.act(delta);       // Update world
            world.render(delta);    // Render world. World render is separate to allow for different camera positions
        }

        stage.act(delta);
        stage.draw();
    }

    // Packet/Object received from server. Only call on client instances.
    private void serverPacketReceived(Object packet) {
        if (packet instanceof Long) { // Long is the seed; world needs to init now
            Gdx.app.postRunnable(() -> {
                world = new World((Long) packet);
                world.freeCamera();
                initInput(new SpectatorInputProcessor(this));
            });
        }
        else if (world == null) {
            // The client is not ready to receive packets yet. All packets are ignored until the seed is sent.
        }
        else if (packet instanceof Player) {  // Player should be synced
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
    void mapClicked(int x, int y, boolean rightClick) {
        tmpV.set(x, y);
        world.screenToWorldCoordinates(tmpV);
        TileVector position = new TileVector().set(tmpV);
        Block block = world.mapClicked(position, rightClick);

        if (block == null) game.getServer().send(position);
        else game.getServer().send(block);
    }

    /** Toggles the pause menu. */
    void togglePausePanel() {
        stage.clear();
        if (!pauseMenuActive) { // Open the pause menu
            stage.addActor(new PausePanel(this));
            Gdx.input.setInputProcessor(stage);
        } else { // Closes the pause menu
            stage.addActor(playerHud);
            Gdx.input.setInputProcessor(inputMultiplexer);
        }
        pauseMenuActive = !pauseMenuActive;
    }

    /** Goes back to the main menu. */
    void returnToMainMenu() {
        dispose();
        game.setScreen(new MenuScreen(game));
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
