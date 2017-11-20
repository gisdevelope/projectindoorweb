package de.hftstuttgart.projectindoorweb.inputHandler;

import de.hftstuttgart.projectindoorweb.application.internal.AssertParam;
import de.hftstuttgart.projectindoorweb.inputHandler.internal.util.ConfigContainer;
import de.hftstuttgart.projectindoorweb.inputHandler.internal.util.LogFileHelper;
import de.hftstuttgart.projectindoorweb.inputHandler.internal.LogFileParser;
import de.hftstuttgart.projectindoorweb.persistence.entities.RadioMap;
import de.hftstuttgart.projectindoorweb.persistence.entities.RadioMapElement;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class LogFileInputHandler implements InputHandler {

    private ExecutorService executorService;
    private List<RadioMap> preProcessingRadioMaps;

    public LogFileInputHandler(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public boolean handleInput(boolean filesForRadiomap, File... inputFiles) {

        AssertParam.throwIfNull(inputFiles,"inputFiles");

        boolean handlingSuccessful = false;

        List<LogFileParser> fileParsers = new ArrayList<>(inputFiles.length);
        for (File inputFile: inputFiles) {
            if(!inputFile.isDirectory() && !inputFile.getPath().contains(".swp")){
                fileParsers.add(new LogFileParser(true, inputFile));
            }
        }

        for (LogFileParser parser: fileParsers) {
            executorService.execute(parser);
        }


        executorService.shutdown();
        try {
            executorService.awaitTermination(ConfigContainer.PARSERS_TERMINATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            handlingSuccessful = true;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        int totalNumberOfMacs = 0;
        List<RadioMapElement> radiomapElements = new ArrayList<>();
        for (LogFileParser parser: fileParsers) {
            if(parser.isParsingFinished()){
                preProcessingRadioMaps.add(new RadioMap(radiomapElements));
            }
        }


        if(ConfigContainer.MERGE_RADIOMAP_ELEMENTS){
            RadioMap tmp = LogFileHelper.mergeRadioMapsBySimilarPositions(preProcessingRadioMaps);
            preProcessingRadioMaps.clear();
            preProcessingRadioMaps.add(tmp);
        }


        return handlingSuccessful;


    }

    @Override
    public List<RadioMap> getGeneratedRadioMaps() {
        return this.preProcessingRadioMaps;
    }
}