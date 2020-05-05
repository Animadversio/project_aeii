package net.toyknight.aeii.manager;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import net.toyknight.aeii.GameContext;
import net.toyknight.aeii.animation.Animator;
import net.toyknight.aeii.animation.EmptyAnimationManager;
import net.toyknight.aeii.campaign.Message;
import net.toyknight.aeii.concurrent.GameEventSyncTask;
import net.toyknight.aeii.entity.*;
import net.toyknight.aeii.network.NetworkManager;
import net.toyknight.aeii.record.GameRecorder;
import net.toyknight.aeii.robot.Robot;
import net.toyknight.aeii.robot.RobotAI;
import net.toyknight.aeii.utils.UnitFactory;
import net.toyknight.aeii.utils.UnitToolkit;
import org.json.JSONObject;

import java.util.LinkedList;

/**
 * @author toyknight  5/28/2015.
 */
public class GameManager implements AnimationListener {

    public static final int STATE_SELECT = 0x1;
    public static final int STATE_MOVE = 0x2;
    public static final int STATE_REMOVE = 0x3;
    public static final int STATE_ACTION = 0x4;
    public static final int STATE_ATTACK = 0x5;
    public static final int STATE_SUMMON = 0x6;
    public static final int STATE_HEAL = 0x7;
    public static final int STATE_PREVIEW = 0x8;
    public static final int STATE_BUY = 0x9;

    private final GameContext context;
    private final GameRecorder game_recorder;
    private final GameEventExecutor event_executor;
    private final OperationExecutor operation_executor;
    private final AnimationDispatcher animation_dispatcher; //EmptyAnimationManager

    private final PositionGenerator position_generator;

    private final Robot robot;
    private final Robot robotAI;

    private GameCore game;
    private UnitToolkit unit_toolkit;
    private GameManagerListener manager_listener;

    private int state;
    protected Unit selected_unit;
    protected Position last_position;

    private final Array<Position> move_path;
    private final ObjectSet<Position> movable_positions;
    private final ObjectSet<Position> attackable_positions;

    private final LinkedList<Message> campaign_messages;

    public GameManager() {
        this(null, new EmptyAnimationManager());
    }

    public GameManager(GameContext context, AnimationDispatcher dispatcher) { //AnimationManager//EmptyAnimationManager
        this.context = context;
        this.animation_dispatcher = dispatcher;
        this.animation_dispatcher.setListener(this);
        this.game_recorder = new GameRecorder(context);
        this.position_generator = new PositionGenerator(this);
        this.operation_executor = new OperationExecutor(this);
        this.event_executor = new GameEventExecutor(this);

        this.robot = new Robot(this); // Note a Robot attach to a context, not a single player. So the robot is simulating playing different sides.
        this.robotAI = new RobotAI(this);

        this.move_path = new Array<Position>();
        this.movable_positions = new ObjectSet<Position>();
        this.attackable_positions = new ObjectSet<Position>();

        this.campaign_messages = new LinkedList<Message>();
    }

    public void setGame(GameCore game) {
        this.game = game;
        this.state = STATE_SELECT;
        getGameEventExecutor().reset();
        getOperationExecutor().reset();
        getAnimationDispatcher().reset();
        getPositionGenerator().reset();
        getGameRecorder().setEnabled(true); // add this line to enable recording by default
        getRobot().initialize();
        getGameRecorder().prepare(getGame());
        this.unit_toolkit = new UnitToolkit(game);
    }

    public GameCore getGame() {
        return game;
    }

    public GameContext getContext() {
        return context;
    }

    public GameRecorder getGameRecorder() {
        return game_recorder;
    }

    public OperationExecutor getOperationExecutor() {
        return operation_executor;
    }

    public GameEventExecutor getGameEventExecutor() {
        return event_executor;
    }

    public AnimationDispatcher getAnimationDispatcher() {
        return animation_dispatcher;
    }

    public PositionGenerator getPositionGenerator() {
        return position_generator;
    }

    public UnitToolkit getUnitToolkit() {
        return unit_toolkit;
    }

    public Robot getRobot() {
        return robot;
    }

    public Robot getRobotAI() {
        return robotAI;
    }

    public void setListener(GameManagerListener listener) {
        this.manager_listener = listener;
    }

    public GameManagerListener getListener() {
        return manager_listener;
    }

    public void setState(int state) {
        if (state != this.state) {
            this.state = state;
            fireStateChangeEvent();
            getPositionGenerator().reset();
        }
    }

    public int getState() {
        return state;
    }

    public boolean isProcessing() {
        return getGameEventExecutor().isProcessing() || getOperationExecutor().isOperating();
    }

    public boolean isAnimating() {
        return getAnimationDispatcher().isAnimating();
    }

    public Animator getCurrentAnimation() {
        return getAnimationDispatcher().getCurrentAnimation();
    }
    // Define macro level operations through GameManager
    @Override
    public void onAnimationFinished() {
        if (!isProcessing()) {
            checkGameState();
        }
    }

    public void onGameEventExecuted(JSONObject event) {
        if (NetworkManager.isConnected()) {
            NetworkManager.submitGameEvent(event);
        }
        getGameRecorder().submitGameEvent(event); // if GameRecorder is enabled this will save the event to its queue
    }

    public void onGameEventFinished() {
        if (!isProcessing()) {
            syncGameEvent();
        }
        if (!isAnimating()) {
            checkGameState();
        }
    }

    public void onOperationFinished() {
        if (!isProcessing()) {
            syncGameEvent();
        }
    }

    private void checkGameState() {
        if (getGame().isGameOver()) {
            fireGameOverEvent();
        } else {
            fireStateChangeEvent();
        }
    }

    public void submitCampaignMessage(Message message) {
        campaign_messages.addLast(message);
    }

    public boolean nextCampaignMessage() {
        if (campaign_messages.size() > 0) {
            campaign_messages.removeFirst();
            return campaign_messages.size() > 0;
        } else {
            return false;
        }
    }

    public Message getCurrentCampaignMessage() {
        if (campaign_messages.size() > 0) {
            return campaign_messages.getFirst();
        } else {
            return null;
        }
    }

    public void update(float delta) throws CheatingException {
        if (campaign_messages.isEmpty()) {
            if (getAnimationDispatcher().isAnimating()) {
                getAnimationDispatcher().updateAnimation(delta);
            } else {
                if (getGameEventExecutor().isProcessing()) {
                    getGameEventExecutor().dispatchGameEvents();
                } else {
                    getOperationExecutor().operate();
                }
            }
        }
        if (canRobotCalculate()) {
            getRobot().calculate();
        }
        if (canRobotAICalculate()) {
            getRobotAI().calculate();
        }
    }

    private boolean canRobotCalculate() {
        return !isAnimating() &&
                !isProcessing() &&
                campaign_messages.isEmpty() &&
                (getGame().getCurrentPlayer().getType() == Player.ROBOT);
    }
    private boolean canRobotAICalculate() {
        return !isAnimating() &&
                !isProcessing() &&
                campaign_messages.isEmpty() &&
                (getGame().getCurrentPlayer().getType() == Player.ROBOTAI);
    }
    // These are the APIs that the GUI uses. When click or touch, run these level functions. Which changes inner states or submit events to operationExecutor
    public void setSelectedUnit(Unit unit) {
        this.selected_unit = unit;
        this.movable_positions.clear();
        getPositionGenerator().reset();
        setLastPosition(getGame().getMap().getPosition(unit));
    }

    public Unit getSelectedUnit() {
        return selected_unit;
    }

    public void setLastPosition(Position position) {
        this.last_position = position;
    }

    public Position getLastPosition() {
        return last_position;
    }

    public void beginPreviewPhase(Unit target) {
        this.selected_unit = target;
        createMovablePositions(true);
        setState(STATE_PREVIEW);
    }

    public void cancelPreviewPhase() {
        setState(STATE_SELECT);
    }

    public void beginMovePhase() {
        createMovablePositions(false);
        setState(STATE_MOVE);
        move_path.clear();
    }

    public void cancelMovePhase() {
        setState(STATE_SELECT);
    }

    public void beginAttackPhase() {
        setState(STATE_ATTACK);
        attackable_positions.clear();
        attackable_positions.addAll(getPositionGenerator().createAttackablePositions(getSelectedUnit(), false));
    }

    public void beginSummonPhase() {
        setState(STATE_SUMMON);
        attackable_positions.clear();
        attackable_positions.addAll(getPositionGenerator().createAttackablePositions(getSelectedUnit(), false));
    }

    public void beginHealPhase() {
        setState(STATE_HEAL);
        attackable_positions.clear();
        attackable_positions.addAll(getPositionGenerator().createAttackablePositions(getSelectedUnit(), true));
    }

    public void beginRemovePhase() {
        createMovablePositions(false);
        setState(STATE_REMOVE);
        move_path.clear();
    }

    public void cancelActionPhase() {
        setState(STATE_ACTION);
    }

    public void doSelect(int x, int y) {
        Unit unit = getGame().getMap().getUnit(x, y);
        if (getGame().isUnitAccessible(unit)) {
            getOperationExecutor().submitOperation(Operation.SELECT, x, y);
            getOperationExecutor().submitOperation(Operation.SELECT_FINISH, x, y);
        }
    }

    public void doMove(int dest_x, int dest_y) {
        if (canSelectedUnitMove(dest_x, dest_y)) {
            int start_x = getSelectedUnit().getX();
            int start_y = getSelectedUnit().getY();
            getOperationExecutor().submitOperation(Operation.MOVE, start_x, start_y, dest_x, dest_y);
            getOperationExecutor().submitOperation(Operation.MOVE_FINISH, dest_x, dest_y);
        }
    }

    public void doReverseMove() {
        int unit_x = getSelectedUnit().getX();
        int unit_y = getSelectedUnit().getY();
        int last_x = getLastPosition().x;
        int last_y = getLastPosition().y;
        getOperationExecutor().submitOperation(Operation.MOVE_REVERSE, unit_x, unit_y, last_x, last_y);
        getOperationExecutor().submitOperation(Operation.MOVE_REVERSE_FINISH, last_x, last_y);
    }

    public void doAttack(int target_x, int target_y) {
        Unit attacker = getSelectedUnit();
        if (getGame().canAttack(attacker, target_x, target_y)) {
            getOperationExecutor().submitOperation(
                    Operation.ATTACK, attacker.getX(), attacker.getY(), target_x, target_y);
            getOperationExecutor().submitOperation(
                    Operation.COUNTER, attacker.getX(), attacker.getY(), target_x, target_y);
            getOperationExecutor().submitOperation(Operation.ACTION_FINISH, attacker.getX(), attacker.getY());
        }
    }

    public void doSummon(int target_x, int target_y) {
        Unit summoner = getSelectedUnit();
        if (getGame().canSummon(summoner, target_x, target_y)) {
            getOperationExecutor().submitOperation(
                    Operation.SUMMON, summoner.getX(), summoner.getY(), target_x, target_y);
            getOperationExecutor().submitOperation(Operation.ACTION_FINISH, summoner.getX(), summoner.getY());
        }
    }

    public void doHeal(int target_x, int target_y) {
        Unit healer = getSelectedUnit();
        if (getGame().canHeal(healer, target_x, target_y)) {
            getOperationExecutor().submitOperation(Operation.HEAL, healer.getX(), healer.getY(), target_x, target_y);
            getOperationExecutor().submitOperation(Operation.ACTION_FINISH, healer.getX(), healer.getY());
        }
    }

    public void doRepair() {
        Unit unit = getSelectedUnit();
        if (getGame().canRepair(unit, unit.getX(), unit.getY())) {
            getOperationExecutor().submitOperation(Operation.REPAIR, unit.getX(), unit.getY());
            getOperationExecutor().submitOperation(Operation.ACTION_FINISH, unit.getX(), unit.getY());
        }
    }

    public void doOccupy() {
        Unit unit = getSelectedUnit();
        if (getGame().canOccupy(unit, unit.getX(), unit.getY())) {
            getOperationExecutor().submitOperation(Operation.OCCUPY, unit.getX(), unit.getY());
            getOperationExecutor().submitOperation(Operation.ACTION_FINISH, unit.getX(), unit.getY());
        }
    }

    public void doBuyUnit(int index, int x, int y) {
        int team = getGame().getCurrentTeam();
        if (canBuy(index, team, x, y)) {
            getOperationExecutor().submitOperation(Operation.BUY, index, team, x, y);
            getOperationExecutor().submitOperation(Operation.SELECT, x, y);
            getOperationExecutor().submitOperation(Operation.SELECT_FINISH, x, y);
        }
    }

    public void doStandbySelectedUnit() {
        Unit unit = getSelectedUnit();
        if (getGame().isUnitAccessible(unit)) {
            getOperationExecutor().submitOperation(Operation.STANDBY, unit.getX(), unit.getY());
        }
    }

    public void doEndTurn() {
        getOperationExecutor().submitOperation(Operation.NEXT_TURN);
        getOperationExecutor().submitOperation(Operation.TURN_STARTED);
    }
    // many utility functions!
    public void createMovablePositions(boolean preview) {
        movable_positions.clear();
        movable_positions.addAll(getPositionGenerator().createMovablePositions(getSelectedUnit(), preview));
    }

    public ObjectSet<Position> getMovablePositions() {
        return movable_positions;
    }

    public ObjectSet<Position> getAttackablePositions() {
        return attackable_positions;
    }

    public Array<Position> getMovePath(int dest_x, int dest_y) {
        if (move_path.size == 0 || checkDestination(dest_x, dest_y)) {
            move_path.clear();
            move_path.addAll(getPositionGenerator().createMovePath(getSelectedUnit(), dest_x, dest_y));
        }
        return move_path;
    }

    private boolean checkDestination(int dest_x, int dest_y) {
        Position current_dest = move_path.get(move_path.size - 1);
        return dest_x != current_dest.x || dest_y != current_dest.y;
    }

    public boolean hasEnemyWithinRange(Unit unit) {
        ObjectSet<Position> attackable_positions = getPositionGenerator().createAttackablePositions(unit, false);
        for (Position position : attackable_positions) {
            if (getSelectedUnit().hasAbility(Ability.DESTROYER) && getGame().getMap().getUnit(position.x, position.y) == null
                    && getGame().getMap().getTile(position.x, position.y).isDestroyable()) {
                return true;
            }
            Unit target = getGame().getMap().getUnit(position.x, position.y);
            if (getGame().isEnemy(unit, target)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAllyCanHealWithinRange(Unit unit) {
        ObjectSet<Position> attackable_positions = getPositionGenerator().createAttackablePositions(unit, true);
        for (Position position : attackable_positions) {
            Unit target = getGame().getMap().getUnit(position.x, position.y);
            if (getGame().canHeal(unit, target)) {
                return true;
            }
        }
        return getGame().canHeal(unit, unit);
    }

    public boolean hasTombWithinRange(Unit unit) {
        ObjectSet<Position> attackable_positions = getPositionGenerator().createAttackablePositions(unit, false);
        for (Position position : attackable_positions) {
            if (getGame().getMap().isTomb(position) && getGame().getMap().getUnit(position) == null) {
                return true;
            }
        }
        return false;
    }

    public boolean canSelectedUnitAct() {
        return !getSelectedUnit().hasAbility(Ability.HEAVY_MACHINE)
                || getSelectedUnit().isAt(last_position.x, last_position.y);
    }

    public boolean canSelectedUnitMove(int dest_x, int dest_y) {
        Position destination = getGame().getMap().getPosition(dest_x, dest_y);
        return getMovablePositions().contains(destination)
                && getGame().isUnitAccessible(getSelectedUnit())
                && getGame().canUnitMove(getSelectedUnit(), dest_x, dest_y);
    }

    public boolean canBuy(int index, int team, int map_x, int map_y) {
        if (getGame().getMap().isWithinMap(map_x, map_y)) {
            Tile tile = getGame().getMap().getTile(map_x, map_y);
            Unit unit = getGame().getMap().getUnit(map_x, map_y);
            if (getGame().isCastleAccessible(tile, team) && getGame().canBuyUponUnit(unit, team)) {
                Unit sample = UnitFactory.isCommander(index) ?
                        UnitFactory.cloneUnit(getGame().getCommander(team)) :
                        UnitFactory.cloneUnit(UnitFactory.getSample(index));
                sample.clearStatus();
                sample.setTeam(team);
                sample.setX(map_x);
                sample.setY(map_y);
                getGame().resetUnit(sample);
                getPositionGenerator().reset();
                ObjectSet<Position> movable_positions = getPositionGenerator().createMovablePositions(sample);
                return movable_positions.size > 0 && getGame().canBuy(index, team);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void syncState(int state, int selected_unit_x, int selected_unit_y) {
        if (selected_unit_x >= 0 && selected_unit_y >= 0) {
            setSelectedUnit(getGame().getMap().getUnit(selected_unit_x, selected_unit_y));
        }
        switch (state) {
            case STATE_ACTION:
            case STATE_BUY:
            case STATE_PREVIEW:
            case STATE_SELECT:
                setState(state);
                break;
            case STATE_ATTACK:
                beginAttackPhase();
                break;
            case STATE_HEAL:
                beginHealPhase();
                break;
            case STATE_MOVE:
                beginMovePhase();
                break;
            case STATE_REMOVE:
                beginRemovePhase();
                break;
            case STATE_SUMMON:
                beginSummonPhase();
                break;
            default:
                //do nothing
        }
    }

    public void syncGameEvent() {
        if (getContext() != null && NetworkManager.isConnected()) {
            getContext().submitAsyncTask(new GameEventSyncTask(getState()) {
                @Override
                public void onFinish(Void result) {
                }

                @Override
                public void onFail(String message) {
                }
            });
        }
    }

    public void fireGameOverEvent() {
        if (getListener() != null) {
            getListener().onGameOver();
        }
    }

    public void fireStateChangeEvent() {
        if (getListener() != null) {
            getListener().onGameManagerStateChanged();
        }
    }

    public void fireMapFocusEvent(int map_x, int map_y, boolean focus_viewport) {
        if (getListener() != null) {
            getListener().onMapFocusRequired(map_x, map_y, focus_viewport);
        }
    }

    public void fireCampaignMessageSubmitEvent() {
        if (getListener() != null) {
            getListener().onCampaignMessageSubmitted();
        }
    }

    public void fireCampaignObjectiveRequestEvent() {
        if (getListener() != null) {
            getListener().onCampaignObjectiveRequested();
        }
    }

    public void fireAttackEvent(Unit attacker, Unit defender) {
        if (getContext() != null) {
            getContext().getCampaignContext().onUnitAttacked(attacker, defender);
        }
    }

    public void fireMoveEvent(Unit unit, int target_x, int target_y) {
        if (getContext() != null) {
            getContext().getCampaignContext().onUnitMoved(unit, target_x, target_y);
        }
    }

    public void fireOccupyEvent(int target_x, int target_y, int team) {
        if (getContext() != null) {
            getContext().getCampaignContext().onTileOccupied(target_x, target_y, team);
        }
    }

    public void fireRepairEvent(int target_x, int target_y) {
        if (getContext() != null) {
            getContext().getCampaignContext().onTileRepaired(target_x, target_y);
        }
    }

    public void fireUnitDestroyEvent(Unit unit) {
        if (getContext() != null) {
            getContext().getCampaignContext().onUnitDestroyed(unit);
        }
    }

    public void fireUnitStandbyEvent(int target_x, int target_y) {
        if (getContext() != null) {
            getContext().getCampaignContext().onUnitStandby(target_x, target_y);
        }
    }

    public void fireTurnStartEvent(int turn) {
        if (getContext() != null) {
            getContext().getCampaignContext().onTurnStart(turn);
        }
    }

}
