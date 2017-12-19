package de.hftstuttgart.projectindoorweb.web;

import de.hftstuttgart.projectindoorweb.application.internal.AssertParam;
import de.hftstuttgart.projectindoorweb.inputHandler.PreProcessingService;
import de.hftstuttgart.projectindoorweb.persistence.PersistencyService;
import de.hftstuttgart.projectindoorweb.persistence.entities.Building;
import de.hftstuttgart.projectindoorweb.persistence.entities.EvaalFile;
import de.hftstuttgart.projectindoorweb.persistence.entities.Floor;
import de.hftstuttgart.projectindoorweb.persistence.entities.Parameter;
import de.hftstuttgart.projectindoorweb.persistence.entities.Position;
import de.hftstuttgart.projectindoorweb.persistence.entities.Project;
import de.hftstuttgart.projectindoorweb.persistence.entities.WifiPositionResult;
import de.hftstuttgart.projectindoorweb.positionCalculator.PositionCalculatorService;
import de.hftstuttgart.projectindoorweb.positionCalculator.WifiPositionCalculatorServiceImpl;
import de.hftstuttgart.projectindoorweb.web.internal.requests.building.AddNewBuilding;
import de.hftstuttgart.projectindoorweb.web.internal.requests.building.BuildingPositionAnchor;
import de.hftstuttgart.projectindoorweb.web.internal.requests.building.GetAllBuildings;
import de.hftstuttgart.projectindoorweb.web.internal.requests.building.GetSingleBuilding;
import de.hftstuttgart.projectindoorweb.web.internal.requests.building.UpdateBuilding;
import de.hftstuttgart.projectindoorweb.web.internal.requests.positioning.BatchPositionResult;
import de.hftstuttgart.projectindoorweb.web.internal.requests.positioning.CalculatedPosition;
import de.hftstuttgart.projectindoorweb.web.internal.requests.positioning.GenerateBatchPositionResults;
import de.hftstuttgart.projectindoorweb.web.internal.requests.positioning.GenerateSinglePositionResult;
import de.hftstuttgart.projectindoorweb.web.internal.requests.positioning.GetAllEvaalEntries;
import de.hftstuttgart.projectindoorweb.web.internal.requests.positioning.GetEvaluationFilesForBuilding;
import de.hftstuttgart.projectindoorweb.web.internal.requests.positioning.GetRadioMapFilesForBuilding;
import de.hftstuttgart.projectindoorweb.web.internal.requests.positioning.ReferencePosition;
import de.hftstuttgart.projectindoorweb.web.internal.requests.positioning.SinglePositionResult;
import de.hftstuttgart.projectindoorweb.web.internal.requests.project.AddNewProject;
import de.hftstuttgart.projectindoorweb.web.internal.requests.project.GetAlgorithmParameters;
import de.hftstuttgart.projectindoorweb.web.internal.requests.project.GetAllAlgorithmTypes;
import de.hftstuttgart.projectindoorweb.web.internal.requests.project.GetAllProjects;
import de.hftstuttgart.projectindoorweb.web.internal.requests.project.LoadSelectedProject;
import de.hftstuttgart.projectindoorweb.web.internal.requests.project.SaveNewProjectParameters;
import de.hftstuttgart.projectindoorweb.web.internal.requests.project.UpdateProject;
import de.hftstuttgart.projectindoorweb.web.internal.util.ParameterHelper;
import de.hftstuttgart.projectindoorweb.web.internal.util.TransmissionHelper;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RestTransmissionServiceImpl implements RestTransmissionService {

    private PersistencyService persistencyService;
    private PreProcessingService preProcessingService;
    private PositionCalculatorService positionCalculatorService;

    public RestTransmissionServiceImpl(PersistencyService persistencyService, PreProcessingService preProcessingService,
                                       PositionCalculatorService positionCalculatorService) {
        this.persistencyService = persistencyService;
        this.preProcessingService = preProcessingService;
        this.positionCalculatorService = positionCalculatorService;
    }

    @Override
    public boolean processEvaalFiles(String buildingIdentifier, boolean evaluationFiles,
                                     MultipartFile[] radioMapFiles, MultipartFile transformedPointsFile) {

        if (buildingIdentifier == null || buildingIdentifier.isEmpty()
                || radioMapFiles == null || radioMapFiles.length == 0) {
            return false;
        }

        if (transformedPointsFile == null) {

            return processEvaalFilesWithoutTransformedPoints(buildingIdentifier, evaluationFiles, radioMapFiles);

        } else {
            return processEvaalFilesWithTransformedPoints(buildingIdentifier, evaluationFiles, radioMapFiles, transformedPointsFile);
        }
    }

    @Override
    public boolean deleteEvaalFile(String evaalFileIdentifier) {
        if (AssertParam.isNullOrEmpty(evaalFileIdentifier)) {
            return false;
        }

        try {
            long evaalFileId = Long.valueOf(evaalFileIdentifier);
            return this.persistencyService.deleteEvaalFile(evaalFileId);
        } catch (NumberFormatException ex) {
            ex.printStackTrace();
            return false;
        }

    }

    @Override
    public List<BatchPositionResult> generatePositionResults(GenerateBatchPositionResults generateBatchPositionResults) {

        List<BatchPositionResult> result = new ArrayList<>();

        if (generateBatchPositionResults == null) {
            return result;
        }

        try {

            long buildingId = generateBatchPositionResults.getBuildingIdentifier();
            Building building = this.persistencyService.getBuildingById(buildingId);

            EvaalFile evaluationFile = this.persistencyService.getEvaalFileForId(generateBatchPositionResults.getEvalFileIdentifier());

            long[] radioMapFileIds = generateBatchPositionResults.getRadioMapFileIdentifiers();
            EvaalFile[] radioMapFiles = new EvaalFile[radioMapFileIds.length];


            for (int i = 0; i < radioMapFileIds.length; i++) {
                radioMapFiles[i] = this.persistencyService.getEvaalFileForId(radioMapFileIds[i]);
            }

            if (evaluationFile != null && TransmissionHelper.areRequestedFilesPresent(radioMapFiles)) {

                String projectName = String.format("AutoGenerated_%s", TransmissionHelper.getFormattedNowTimestamp());
                String algorithmType = generateBatchPositionResults.getAlgorithmType();
                Set<SaveNewProjectParameters> saveNewProjectParamaters = generateBatchPositionResults.getProjectParameters();
                Long evalFileId = evaluationFile.getId();

                long projectId = this.persistencyService.createNewProject(projectName, algorithmType, saveNewProjectParamaters, buildingId, evalFileId, radioMapFileIds);
                Project autoGeneratedProject = this.persistencyService.getProjectById(projectId);

                if (autoGeneratedProject != null) {
                    List<WifiPositionResult> retrievedWifiResults =
                            (List<WifiPositionResult>) this.positionCalculatorService.
                                    calculatePositions(building, autoGeneratedProject,
                                            evaluationFile, radioMapFiles);

                    result = TransmissionHelper.convertToBatchPositionResults(
                            retrievedWifiResults, building);
                    if (generateBatchPositionResults.isWithPixelPosition()) {
                        result = TransmissionHelper
                                .convertCalculatedResultsToPixelPositions(result, building);
                    }
                    this.persistencyService.deleteProject(projectId);

                }

            }

        } catch (NumberFormatException ex) {
            ex.printStackTrace();

        }

        return result;

    }

    @Override
    public SinglePositionResult getPositionForWifiReading(GenerateSinglePositionResult generateSinglePositionResult) {

        SinglePositionResult result = createEmptySinglePositionResult();

        if (generateSinglePositionResult == null) {

            return result;
        }

        try {
            long buildingId = generateSinglePositionResult.getBuildingIdentifier();
            Building building = this.persistencyService.getBuildingById(buildingId);

            long[] radioMapFileIds = generateSinglePositionResult.getRadioMapFileIdentifiers();
            EvaalFile[] radioMapFiles = new EvaalFile[radioMapFileIds.length];


            for (int i = 0; i < radioMapFileIds.length; i++) {
                radioMapFiles[i] = this.persistencyService.getEvaalFileForId(radioMapFileIds[i]);
            }

            String projectName = String.format("AutoGenerated_%s", TransmissionHelper.getFormattedNowTimestamp());
            String algorithmType = generateSinglePositionResult.getAlgorithmType();
            Set<SaveNewProjectParameters> saveNewProjectParameters = generateSinglePositionResult.getSaveNewProjectParameters();
            Long evalFileId = generateSinglePositionResult.getEvalFileIdentifier();

            if (evalFileId == null) {
                evalFileId = -1L;
            }

            long projectId = this.persistencyService.createNewProject(projectName, algorithmType, saveNewProjectParameters,
                    buildingId, evalFileId, radioMapFileIds);
            Project autoGeneratedProject = this.persistencyService.getProjectById(projectId);

            if (autoGeneratedProject != null && TransmissionHelper.areRequestedFilesPresent(radioMapFiles)) {
                WifiPositionResult retrievedWifiResult = (WifiPositionResult) this.positionCalculatorService
                        .calculateSinglePosition(building, autoGeneratedProject,
                                generateSinglePositionResult.getWifiReadings(), radioMapFiles);
                if (generateSinglePositionResult.isWithPixelPosition()) {
                    retrievedWifiResult = TransmissionHelper.convertCalculatedResultToPixelPosition(retrievedWifiResult, building);
                }
                result = TransmissionHelper.convertToCalculatedPosition(retrievedWifiResult);
                this.persistencyService.deleteProject(projectId);

            }
        } catch (NumberFormatException ex) {
            ex.printStackTrace();
        }

        return result;


    }

    @Override
    public List<BatchPositionResult> getPositionResultsForProjectIdentifier(String projectIdentifier) {

        List<BatchPositionResult> result = new ArrayList<>();

        if (AssertParam.isNullOrEmpty(projectIdentifier)) {
            return result;
        }

        return result;


    }

    @Override
    public long addNewProject(AddNewProject addNewProject) {

        if (addNewProject == null) {
            return -1;
        }

        return this.persistencyService.createNewProject(addNewProject.getProjectName(),
                addNewProject.getAlgorithmType(),
                addNewProject.getSaveNewProjectParametersSet(),
                addNewProject.getBuildingIdentifier(),
                addNewProject.getEvalFileIdentifier(),
                addNewProject.getRadioMapFileIdentifiers());

    }

    @Override
    public boolean updateProject(UpdateProject updateProject) {


        if (updateProject == null) {
            return false;
        }


        return this.persistencyService.updateProject(updateProject.getProjectIdentifier(),
                updateProject.getProjectName(),
                updateProject.getAlgorithmType(),
                updateProject.getSaveNewProjectParametersSet(),
                updateProject.getBuildingIdentifier(),
                updateProject.getEvalFileIdentifier(),
                updateProject.getRadioMapFileIdentifiers());


    }

    @Override
    public boolean deleteProject(String projectIdentifier) {

        if (AssertParam.isNullOrEmpty(projectIdentifier)) {
            return false;
        }

        try {
            long projectId = Long.parseLong(projectIdentifier);
            return this.persistencyService.deleteProject(projectId);
        } catch (NumberFormatException ex) {
            return false;
        }

    }

    @Override
    public LoadSelectedProject loadSelectedProject(String projectIdentifier) {

        if (AssertParam.isNullOrEmpty(projectIdentifier)) {
            return createEmptyProjectElement();
        }

        try {
            long projectId = Long.parseLong(projectIdentifier);
            Project project = this.persistencyService.getProjectById(projectId);

            if (project != null) {

                Set<SaveNewProjectParameters> saveNewProjectParameters = TransmissionHelper.convertToExternalProjectParameters(project.getProjectParameters());
                String projectName = project.getProjectName();
                String algorithmType = TransmissionHelper.convertToExternalAlgorithmType(project.getCalculationAlgorithm());
                Building building = project.getBuilding();
                long buildingIdentifier = -1;
                if (building != null) {
                    buildingIdentifier = project.getBuilding().getId();
                }
                EvaalFile evaluationFile = project.getEvaluationFile();
                long evaluationFileIdentidier = -1;
                if (evaluationFile != null) {
                    evaluationFileIdentidier = project.getEvaluationFile().getId();
                }
                long[] radioMapFileIdentifiers = new long[]{-1};
                List<EvaalFile> evalFiles = project.getEvaalFiles();
                if (evalFiles != null && !evalFiles.isEmpty()) {
                    radioMapFileIdentifiers = TransmissionHelper.getEvaalFileIds(project.getEvaalFiles());
                }
                return new LoadSelectedProject(projectId, saveNewProjectParameters, projectName, algorithmType,
                        buildingIdentifier, evaluationFileIdentidier, radioMapFileIdentifiers);
            }

        } catch (NumberFormatException ex) {
            ex.printStackTrace();
        }

        return createEmptyProjectElement();
    }

    @Override
    public List<GetAllProjects> getAllProjects() {

        List<Project> projects = this.persistencyService.getAllProjects();

        return convertToProjectElements(projects);

    }

    @Override
    public List<GetAllBuildings> getAllBuildings() {
        List<Building> buildings = this.persistencyService.getAllBuildings();

        return TransmissionHelper.convertToBuildingSmallJsonWrapper(buildings);
    }

    @Override
    public GetSingleBuilding getSingleBuilding(String buildingIdentifier) {


        GetSingleBuilding result = createEmptySingleBuildingResult();

        if (AssertParam.isNullOrEmpty(buildingIdentifier)) {
            return result;
        }

        try {
            long buildingId = Long.valueOf(buildingIdentifier);
            Building building = this.persistencyService.getBuildingById(buildingId);

            result = TransmissionHelper.convertToGetSingleBuildingResultObject(building);

        } catch (NumberFormatException ex) {
            ex.printStackTrace();
        }

        return result;


    }

    @Override
    public boolean updateBuilding(UpdateBuilding updateBuilding) {

        if (updateBuilding == null) {
            return false;
        }

        long buildingId = updateBuilding.getBuildingId();
        String buildingName = updateBuilding.getBuildingName();
        int imagePixelWidth = updateBuilding.getImagePixelWidth();
        int imagePixelHeight = updateBuilding.getImagePixelHeight();

        Position northWest = TransmissionHelper.convertPositionAnchorToPosition(updateBuilding.getNorthWest());
        Position northEast = TransmissionHelper.convertPositionAnchorToPosition(updateBuilding.getNorthEast());
        Position southEast = TransmissionHelper.convertPositionAnchorToPosition(updateBuilding.getSouthEast());
        Position southWest = TransmissionHelper.convertPositionAnchorToPosition(updateBuilding.getSouthWest());

        Position buildingCenterPoint = TransmissionHelper.convertPositionAnchorToPosition(updateBuilding.getBuildingCenterPoint());
        double rotationAngle = updateBuilding.getRotationAngle();
        double metersPerPixel = updateBuilding.getMetersPerPixel();


        return this.persistencyService.updateBuilding(buildingId, buildingName, imagePixelWidth, imagePixelHeight,
                northWest, northEast, southEast, southWest, buildingCenterPoint, rotationAngle, metersPerPixel);


    }

    @Override
    public boolean addFloorToBuilding(String buildingIdentifier, String floorIdentifier, String floorName, MultipartFile floorMapFile) {

        if (AssertParam.isNullOrEmpty(buildingIdentifier) || AssertParam.isNullOrEmpty(floorIdentifier) ||
                AssertParam.isNullOrEmpty(floorName) || floorMapFile == null) {
            return false;
        }

        try {
            long buildingId = Long.valueOf(buildingIdentifier);
            Building buildingFromDatabase = this.persistencyService.getBuildingById(buildingId);

            if (buildingFromDatabase != null) {

                long floorId = Long.valueOf(floorIdentifier);
                Floor floor = TransmissionHelper.getBuildingFloorById(floorId, buildingFromDatabase);

                if (floor != null) {

                    File floorMapLocalFile = TransmissionHelper.convertMultipartFileToLocalFile(floorMapFile);
                    return this.persistencyService.updateBuildingFloor(buildingFromDatabase, floor, floorMapLocalFile);
                }
            }

        } catch (NumberFormatException | IOException e) {
            e.printStackTrace();
        }

        return false;


    }

    @Override
    public File getFloorMap(String floorIdentifier) {
        if (AssertParam.isNullOrEmpty(floorIdentifier)) {
            return null;
        }

        try {
            long floorId = Long.valueOf(floorIdentifier);
            return this.persistencyService.getFloorMapByFloorId(floorId);

        } catch (NumberFormatException | IOException ex) {
            ex.printStackTrace();
        }

        return null;
    }

    @Override
    public boolean deleteBuilding(String buildingIdentifier) {
        if (AssertParam.isNullOrEmpty(buildingIdentifier)) {
            return false;
        }

        try {
            long buildingId = Long.valueOf(buildingIdentifier);
            return this.persistencyService.deleteBuilding(buildingId);
        } catch (NumberFormatException ex) {
            ex.printStackTrace();
            return false;
        }

    }

    @Override
    public List<GetAllAlgorithmTypes> getAllAlgorithmTypes() {
        List<GetAllAlgorithmTypes> result = new ArrayList<>();
        ParameterHelper helper = ParameterHelper.getInstance();

        GetAllAlgorithmTypes getAllAlgorithmTypes = new GetAllAlgorithmTypes(
                WifiPositionCalculatorServiceImpl.class.getName(),
                "WIFI",
                helper.getParametersForAlgorithmType("WIFI"));

        result.add(getAllAlgorithmTypes);

        return result;
    }


    @Override
    public List<GetAlgorithmParameters> getAllParameters() {
        return ParameterHelper.getInstance().getAllParameters();
    }

    @Override
    public List<GetAlgorithmParameters> getParametersForAlgorithm(String algorithmType) {

        ParameterHelper helper = ParameterHelper.getInstance();
        List<GetAlgorithmParameters> result = new ArrayList<>();

        if (AssertParam.isNullOrEmpty(algorithmType)) {
            return result;
        }

        result = helper.getParametersForAlgorithmType(algorithmType);

        return result;
    }


    @Override
    public List<GetEvaluationFilesForBuilding> getEvaluationFilesForBuilding(String buildingIdentifier) {

        List<GetEvaluationFilesForBuilding> result = new ArrayList<>();

        if (AssertParam.isNullOrEmpty(buildingIdentifier)) {
            return result;
        }

        try {

            long buildingId = Long.valueOf(buildingIdentifier);
            Building building = this.persistencyService.getBuildingById(buildingId);
            if (building != null) {
                List<EvaalFile> evaalFiles = this.persistencyService.getEvaluationFilesForBuilding(building);
                result = TransmissionHelper.convertToEvaluationEntries(evaalFiles);
            }

        } catch (NumberFormatException ex) {
            ex.printStackTrace();
        } finally {
            return result;
        }


    }

    @Override
    public List<GetRadioMapFilesForBuilding> getRadioMapFilesForBuilding(String buildingIdentifier) {

        List<GetRadioMapFilesForBuilding> result = new ArrayList<>();
        if (AssertParam.isNullOrEmpty(buildingIdentifier)) {
            return result;
        }

        try {

            long buildingId = Long.valueOf(buildingIdentifier);
            Building building = this.persistencyService.getBuildingById(buildingId);
            if (building != null) {
                List<EvaalFile> evaalFiles = this.persistencyService.getRadioMapFilesForBuiling(building);
                result = TransmissionHelper.convertToRadioMapEntry(evaalFiles);
            }


        } catch (NumberFormatException ex) {
            ex.printStackTrace();
        } finally {
            return result;
        }

    }

    @Override
    public List<GetAllEvaalEntries> getAllEvaalEntries() {
        List<GetAllEvaalEntries> result = new ArrayList<>();

        List<EvaalFile> evaalFilesFromDatabase = this.persistencyService.getAllEvaalFiles();
        result = TransmissionHelper.convertToGetAlLEvaalEntries(evaalFilesFromDatabase);

        return result;
    }

    @Override
    public long addNewBuilding(AddNewBuilding addNewBuilding) {

        if (addNewBuilding == null) {
            return -1;
        }
        try {

            String buildingName = addNewBuilding.getBuildingName();
            int numberOfFloors = addNewBuilding.getNumberOfFloors();
            int imagePixelWidth = addNewBuilding.getImagePixelWidth();
            int imagePixelHeight = addNewBuilding.getImagePixelHeight();
            BuildingPositionAnchor southEastAnchor = addNewBuilding.getSouthEast();
            BuildingPositionAnchor southWestAnchor = addNewBuilding.getSouthWest();
            BuildingPositionAnchor northEastAnchor = addNewBuilding.getNorthEast();
            BuildingPositionAnchor northWestAnchor = addNewBuilding.getNorthWest();
            BuildingPositionAnchor buildingCenterPoint = addNewBuilding.getBuildingCenterPoint();
            double rotationAngle = addNewBuilding.getRotationAngle();
            double metersPerPixel = addNewBuilding.getMetersPerPixel();

            return this.persistencyService.addNewBuilding(buildingName, numberOfFloors, imagePixelWidth, imagePixelHeight,
                    southEastAnchor, southWestAnchor, northEastAnchor, northWestAnchor, buildingCenterPoint, rotationAngle, metersPerPixel);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private boolean processEvaalFilesWithoutTransformedPoints(String buildingIdentifier, boolean evaluationFiles, MultipartFile[] radioMapFiles) {
        File[] radioMapFileArray = new File[radioMapFiles.length];

        try {
            for (int i = 0; i < radioMapFiles.length; i++) {
                radioMapFileArray[i] = TransmissionHelper.convertMultipartFileToLocalFile(radioMapFiles[i]);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        try {
            long buildingId = Long.valueOf(buildingIdentifier);
            Building building = this.persistencyService.getBuildingById(buildingId);

            if (building != null) {
                List<EvaalFile> processedEvaalFiles = this.preProcessingService.processIntoLogFiles(building, evaluationFiles, radioMapFileArray);
                return this.persistencyService.saveEvaalFiles(processedEvaalFiles);
            } else {
                return false;
            }


        } catch (NumberFormatException ex) {
            return false;
        }
    }


    private boolean processEvaalFilesWithTransformedPoints(String buildingIdentifier, boolean evaluationFiles, MultipartFile[] radioMapFiles,
                                                           MultipartFile transformedPointsFile) {

        File[] radioMapFileArray = new File[radioMapFiles.length];

        try {
            for (int i = 0; i < radioMapFiles.length; i++) {
                radioMapFileArray[i] = TransmissionHelper.convertMultipartFileToLocalFile(radioMapFiles[i]);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;

    }

    private BatchPositionResult createEmptyBatchPositionResult() {
        CalculatedPosition calculatedPosition = new CalculatedPosition(0, 0, 0, false);
        ReferencePosition referencePosition = new ReferencePosition(-1, 0, 0, 0, false);
        return new BatchPositionResult(calculatedPosition, referencePosition, 0);
    }

    private SinglePositionResult createEmptySinglePositionResult() {
        return new SinglePositionResult(0, 0, 0, false);
    }

    private GetSingleBuilding createEmptySingleBuildingResult() {
        return new GetSingleBuilding(-1, "", -1, -1, -1, null, null,
                null, null, null, -1.0, -1.0, null, null);
    }

    private LoadSelectedProject createEmptyProjectElement() {
        return new LoadSelectedProject(-1L, null, "", "",
                -1L, -1L, new long[]{-1L});
    }

    private List<GetAllProjects> convertToProjectElements(List<Project> projects) {

        List<GetAllProjects> result = new ArrayList<>(projects.size());

        String buildingName = "";
        for (Project project :
                projects) {
            if (project.getBuilding() != null) {
                buildingName = project.getBuilding().getBuildingName();
            }
            result.add(new GetAllProjects(project.getId(), project.getProjectName(), buildingName));
        }

        return result;

    }

    private Set<SaveNewProjectParameters> getProjectParametersFromInternalEntity(List<Parameter> parameters) {

        Set<SaveNewProjectParameters> saveNewProjectParameters = new LinkedHashSet<>();

        for (Parameter parameter :
                parameters) {
            saveNewProjectParameters.add(new SaveNewProjectParameters(parameter.getParameterName(), parameter.getParameterValue()));
        }

        return saveNewProjectParameters;

    }
}
