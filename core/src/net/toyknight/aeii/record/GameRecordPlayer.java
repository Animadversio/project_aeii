package net.toyknight.aeii.record;

import com.badlogic.gdx.Gdx;
import net.toyknight.aeii.GameContext;
import net.toyknight.aeii.manager.GameManager;
import net.toyknight.aeii.manager.GameEvent;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author toyknight 10/30/2015.
 */
public class GameRecordPlayer {

    private static final String TAG = "Record Player";
    private Float step;
    private final GameContext context;

    private GameRecordPlayerListener listener;

    private GameRecord record;
    private float playback_delay;
    private boolean playback_finished;

    public GameRecordPlayer(GameContext context) {
        this.context = context;
        this.step = Float.parseFloat(context.getConfiguration().get("replay_step"));
    }

    public GameContext getContext() {
        return context;
    }

    public GameManager getManager() {
        return getContext().getGameManager();
    }

    public GameRecord getRecord() {
        return record;
    }

    public void setListener(GameRecordPlayerListener listener) {
        this.listener = listener;
    }

    public void setRecord(GameRecord record) {
        this.record = record;
        playback_delay = 0f;
        playback_finished = false;
    }

    public void reset() {
        record = null;
    }

    public void update(float delta) {
        try {
            if (getRecord() != null) {
                if (getRecord().getEvents().isEmpty()) { // go through the queue of events and say it's finished
                    if (!playback_finished) {
                        playback_finished = true;
                        fireRecordFinishEvent(); // screen needs to do this
                    }
                } else {
                    JSONObject preview = getRecord().getEvents().peek();
                    int type = preview.getInt("type");
                    if (type == GameEvent.TILE_DESTROY || type == GameEvent.ATTACK) {
                        JSONObject event = getRecord().getEvents().poll();
                        getManager().getGameEventExecutor().submitGameEvent(event);
                    } else {
                        if (playback_delay < step) {// Note this play back delay is counting the frame rate!0.05f
                            playback_delay += delta;
                        } else {
                            playback_delay = 0f;
                            JSONObject event = getRecord().getEvents().poll();
                            getManager().getGameEventExecutor().submitGameEvent(event);
                        }
                    }
                }
            }
        } catch (JSONException ex) {
            Gdx.app.log(TAG, ex.toString());
        }
    }

    private void fireRecordFinishEvent() {
        if (listener != null) {
            listener.onRecordPlaybackFinished();
        }
    }

}
