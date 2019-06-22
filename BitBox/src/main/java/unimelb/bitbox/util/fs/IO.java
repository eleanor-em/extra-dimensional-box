package unimelb.bitbox.util.fs;

import functional.algebraic.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class IO {
    private IO() {}

    public static Result<String, IOException> fileToString(String filename) {
        return Result.of(() -> {
            StringBuilder content = new StringBuilder();
            Files.lines(Paths.get(filename)).forEach(line -> content.append(line).append("\n"));
            return content.toString();
        });
    }
}
