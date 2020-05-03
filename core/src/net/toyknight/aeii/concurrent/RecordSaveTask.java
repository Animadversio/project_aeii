package net.toyknight.aeii.concurrent;

import com.badlogic.gdx.files.FileHandle;
import com.esotericsoftware.kryo.io.Output;
import net.toyknight.aeii.record.GameRecord;
import net.toyknight.aeii.utils.FileProvider;
import net.toyknight.aeii.utils.GameToolkit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.file.StandardOpenOption;

/**
 * @author toyknight 5/9/2016.
 */
public class RecordSaveTask extends AsyncTask<Void> {

    private final GameRecord record;

    public RecordSaveTask(GameRecord record) {
        this.record = record;
    }

    @Override
    public Void doTask() throws Exception {
        String filename = GameToolkit.createFilename(GameToolkit.RECORD);
        FileHandle record_file = FileProvider.getUserFile("save/" + filename);
        Output output = new Output(record_file.write(false));
        String content = record.toJson().toString();
        output.writeInt(GameToolkit.RECORD);
        output.writeString(content);
        output.flush();
        output.close();
        content = record.toJson().toString(2);//.toString() will result in highly compressed JSON
        System.out.println(content);
        Path filePath = Paths.get(System.getProperty("user.home") + "/.aeii/","save/" + filename + ".json");
        Files.writeString(filePath, content, StandardOpenOption.CREATE_NEW);
        return null;
    }

    @Override
    public void onFinish(Void result) {
    }

    @Override
    public void onFail(String message) {
        System.err.println("Error saving game record: " + message);
    }

}
