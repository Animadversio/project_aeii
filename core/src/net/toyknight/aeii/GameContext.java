package net.toyknight.aeii;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.PropertiesUtils;
import net.toyknight.aeii.animation.AnimationManager;
import net.toyknight.aeii.animation.Animator;
import net.toyknight.aeii.campaign.CampaignContext;
import net.toyknight.aeii.entity.*;
import net.toyknight.aeii.concurrent.AsyncTask;
import net.toyknight.aeii.manager.GameManager;
import net.toyknight.aeii.manager.GameManagerListener;
import net.toyknight.aeii.manager.RoomManager;
import net.toyknight.aeii.record.GameRecord;
import net.toyknight.aeii.record.GameRecordPlayer;
import net.toyknight.aeii.renderer.BorderRenderer;
import net.toyknight.aeii.renderer.FontRenderer;
import net.toyknight.aeii.screen.*;
import net.toyknight.aeii.screen.wiki.Wiki;
import net.toyknight.aeii.utils.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameContext extends Game implements GameManagerListener {

    public static final Object RENDER_LOCK = new Object();

    public static final String VERSION = "1.1.3";
    private static final String TAG = "Main";

    private final int TILE_SIZE;
    private final Platform PLATFORM;

    private boolean initialized = false;

    private ExecutorService executor;

    private Skin skin;

    private ObjectMap<String, String> configuration;

    private ResourceManager resource_manager;

    private GameManager game_manager;

    private GameRecordPlayer record_player;

    private CampaignContext campaign_context;

    private RoomManager room_manager;

    private Screen previous_screen;

    private Wiki wiki;

    private MainMenuScreen main_menu_screen;
    private MapEditorScreen map_editor_screen;
    private LobbyScreen lobby_screen;
    private NetGameCreateScreen net_game_create_screen;
    private SkirmishGameCreateScreen skirmish_game_create_screen;
    private GameScreen game_screen;
    private StatisticsScreen statistics_screen;
    private MapManagementScreen map_management_screen;
    private CampaignScreen campaign_screen;

    public GameContext(Platform platform, int ts) {
        this.TILE_SIZE = ts;
        this.PLATFORM = platform;
    }

    @Override
    public void create() {
        try {
            executor = Executors.newSingleThreadExecutor();
            FileProvider.setPlatform(PLATFORM);
            Language.initialize();
            TileFactory.loadTileData();
            UnitFactory.loadUnitData();
            resource_manager = new ResourceManager();
            resource_manager.prepare(TILE_SIZE);
            Animator.setTileSize(TILE_SIZE);

            LoadingScreen loading_screen = new LoadingScreen(this);
            Gdx.input.setCatchBackKey(true);
            setScreen(loading_screen);
        } catch (AEIIException ex) {
            Gdx.app.log(TAG, ex.toString() + "; Cause: " + ex.getCause().toString());
        }
    }

    public void initialize() {
        if (!initialized) {
            try {
                loadConfiguration();
                AudioManager.setSEVolume(getSEVolume());
                AudioManager.setMusicVolume(getMusicVolume());
                resource_manager.initialize();
                FontRenderer.initialize(TILE_SIZE);
                TileValidator.initialize();
                BorderRenderer.initialize();

                skin = getResourceManager().getSkin();
                skin.get(TextButton.TextButtonStyle.class).font = ResourceManager.getTextFont();
                skin.get(TextField.TextFieldStyle.class).font = ResourceManager.getTextFont();
                skin.get(Label.LabelStyle.class).font = ResourceManager.getTextFont();
                skin.get(Dialog.WindowStyle.class).titleFont = ResourceManager.getTextFont();
                skin.get(List.ListStyle.class).font = ResourceManager.getTextFont();

                game_manager = new GameManager(this, new AnimationManager());
                game_manager.getGameEventExecutor().setCheckEventValue(true);
                game_manager.setListener(this);

                room_manager = new RoomManager();

                StageScreen.initializePrompt(getSkin(), TILE_SIZE);
                main_menu_screen = new MainMenuScreen(this);
                map_editor_screen = new MapEditorScreen(this);
                lobby_screen = new LobbyScreen(this);
                net_game_create_screen = new NetGameCreateScreen(this);
                skirmish_game_create_screen = new SkirmishGameCreateScreen(this);
                game_screen = new GameScreen(this);
                statistics_screen = new StatisticsScreen(this);
                map_management_screen = new MapManagementScreen(this);
                campaign_screen = new CampaignScreen(this);
                wiki = new Wiki(main_menu_screen);

                record_player = new GameRecordPlayer(this);
                record_player.setListener(game_screen);

                campaign_context = new CampaignContext(this);

                initialized = true;
            } catch (AEIIException ex) {
                Gdx.app.log(TAG, ex.toString() + "; Cause: " + ex.getCause().toString());
            }
        }
    }


    public boolean initialized() {
        return initialized;
    }

    public ResourceManager getResourceManager() {
        return resource_manager;
    }

    public Wiki getWiki() {
        return wiki;
    }

    private void loadConfiguration() throws AEIIException {
        FileHandle config_file = FileProvider.getUserFile("user.config");
        configuration = new ObjectMap<String, String>();
        try {
            if (config_file.exists() && !config_file.isDirectory()) {
                InputStreamReader reader = new InputStreamReader(config_file.read(), "UTF8");
                PropertiesUtils.load(configuration, reader);
            } else {
                configuration.put("username", "undefined");
                configuration.put("se_volume", "0.5");
                configuration.put("music_volume", "0.5");
                OutputStreamWriter writer = new OutputStreamWriter(config_file.write(false), "UTF8");
                PropertiesUtils.store(configuration, writer, "aeii user configuration file");
            }
        } catch (IOException ex) {
            throw new AEIIException(ex.getMessage());
        }
    }

    public void updateConfiguration(String key, String value) {
        configuration.put(key, value);
    }

    public void saveConfiguration() {
        FileHandle config_file = FileProvider.getUserFile("user.config");
        try {
            OutputStreamWriter writer = new OutputStreamWriter(config_file.write(false), "UTF8");
            PropertiesUtils.store(configuration, writer, "aeii user configure file");
        } catch (IOException ex) {
            Gdx.app.log(TAG, ex.toString());
        }
    }

    public int getTileSize() {
        return TILE_SIZE;
    }

    public Platform getPlatform() {
        return PLATFORM;
    }

    public ObjectMap<String, String> getConfiguration() {
        return configuration;
    }

    public String getUsername() {
        return getConfiguration().get("username", "undefined");
    }

    public float getSEVolume() {
        return Float.parseFloat(configuration.get("se_volume", "0.5"));
    }

    public float getMusicVolume() {
        return Float.parseFloat(configuration.get("music_volume", "0.5"));
    }

    public int getCampaignProgress(String campaign_code) {
        if (getConfiguration().containsKey(campaign_code)) {
            return Integer.parseInt(getConfiguration().get(campaign_code));
        } else {
            updateConfiguration(campaign_code, Integer.toString(0));
            saveConfiguration();
            return 0;
        }
    }

    public String getVersion() {
        return VERSION;
    }

    public String getVerificationString() {
        String V_STRING = TileFactory.getVerificationString() + UnitFactory.getVerificationString() + VERSION;
        return new Encryptor().encryptString(V_STRING);
    }

    public Skin getSkin() {
        return skin;
    }

    public GameManager getGameManager() {
        return game_manager;
    }

    public GameRecordPlayer getRecordPlayer() {
        return record_player;
    }

    public CampaignContext getCampaignContext() {
        return campaign_context;
    }

    public RoomManager getRoomManager() {
        return room_manager;
    }

    public GameCore getGame() {
        return getGameManager().getGame();
    }

    public void gotoMainMenuScreen(boolean restart_bgm) {
        if (restart_bgm) {
            AudioManager.loopMainTheme();
        }
        gotoScreen(main_menu_screen);
    }

    public void gotoMapEditorScreen() {
        AudioManager.stopCurrentBGM();
        gotoScreen(map_editor_screen);
    }

    public void gotoGameScreen(GameCore game) {
        AudioManager.playRandomBGM("bg_good.mp3");
        if (!game.initialized()) {
            game.initialize();
        }
        getGameManager().setGame(game);
        gotoScreen(game_screen);
    }

    public void gotoGameScreen(String campaign_code, int stage) {
        try {
            getCampaignContext().setCurrentCampaign(campaign_code);
            getCampaignContext().setCurrentStage(stage);
            GameCore game = GameToolkit.createCampaignGame(getCampaignContext().getCurrentCampaign().getCurrentStage());
            gotoGameScreen(game);
            getCampaignContext().onGameStart();
        } catch (AEIIException ex) {
            if (getScreen() instanceof StageScreen) {
                ((StageScreen) getScreen()).showPrompt(Language.getText("MSG_ERR_BMF"), null);
            }
        }
    }

    public void gotoGameScreen(GameSave save) {
        AudioManager.playRandomBGM("bg_good.mp3");
        getGameManager().setGame(save.getGame());
        gotoScreen(game_screen);
    }

    public void gotoGameScreen(GameRecord record) {
        AudioManager.playRandomBGM("bg_good.mp3");
        getGameManager().setGame(record.getGame());
        getRecordPlayer().setRecord(record);
        gotoScreen(game_screen);
    }

    public void gotoLobbyScreen() {
        gotoScreen(lobby_screen);
    }

    public void gotoNetGameCreateScreen() {
        AudioManager.stopCurrentBGM();
        gotoScreen(net_game_create_screen);
    }

    public void gotoStatisticsScreen(GameCore game) {
        getRecordPlayer().reset();
        getGameManager().getGameRecorder().save();
        statistics_screen.setGame(game);
        gotoScreen(statistics_screen);
    }

    public void gotoSkirmishGameCreateScreen() {
        gotoScreen(skirmish_game_create_screen);
    }

    public void gotoMapManagementScreen() {
        gotoScreen(map_management_screen);
    }

    public void gotoCampaignScreen() {
        gotoScreen(campaign_screen);
    }

    public void gotoPreviousScreen() {
        this.gotoScreen(previous_screen);
    }

    public void gotoScreen(Screen screen) {
        this.previous_screen = getScreen();
        this.setScreen(screen);
    }

    public void submitAsyncTask(AsyncTask task) {
        executor.submit(task);
    }

    @Override
    public void onMapFocusRequired(int map_x, int map_y, boolean focus_viewport) {
        game_screen.focus(map_x, map_y, focus_viewport);
    }

    @Override
    public void onGameManagerStateChanged() {
        game_screen.update();
    }

    @Override
    public void onCampaignMessageSubmitted() {
        game_screen.showCampaignMessage();
    }

    @Override
    public void onGameOver() {
        if (getGame().getType() == GameCore.CAMPAIGN) {
            if (getCampaignContext().getCurrentCampaign().getCurrentStage().isCleared()) {
                boolean has_next_stage = getCampaignContext().getCurrentCampaign().nextStage();
                if (has_next_stage) {
                    String campaign_code = getCampaignContext().getCurrentCampaign().getCode();
                    int stage_number = getCampaignContext().getCurrentCampaign().getCurrentStage().getStageNumber();

                    if (stage_number > getCampaignProgress(campaign_code)) {
                        updateConfiguration(campaign_code, Integer.toString(stage_number));
                        saveConfiguration();
                    }
                    gotoGameScreen(campaign_code, stage_number);
                } else {
                    gotoCampaignScreen();
                }
            } else {
                gotoCampaignScreen();
            }
            AudioManager.loopMainTheme();
        }
        if (getGame().getType() == GameCore.SKIRMISH) {
            gotoStatisticsScreen(getGame());
        }
    }

    @Override
    public void render() {
        synchronized (RENDER_LOCK) {
            Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            super.render();
        }
    }

    @Override
    public void dispose() {
    }

    public static void setButtonEnabled(Button button, boolean enabled) {
        button.setDisabled(!enabled);
        if (enabled) {
            button.setTouchable(Touchable.enabled);
        } else {
            button.setTouchable(Touchable.disabled);
        }
    }

}