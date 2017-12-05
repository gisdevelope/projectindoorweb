package de.hftstuttgart.projectindoorweb.positionCalculator;

import de.hftstuttgart.projectindoorweb.persistence.entities.*;

import java.io.File;
import java.util.List;

public interface PositionCalculatorService {

    List<? extends PositionResult> calculatePositions(EvaalFile evaluationFile, EvaalFile[] radioMapFiles,
                                                      Building building, boolean pixelPositionRequired);

    <T extends PositionResult> T calculateSinglePosition(String[] wifiReadings, EvaalFile[] radioMapFiles,
                                                         Building building, boolean pixelPositionRequired);

}
