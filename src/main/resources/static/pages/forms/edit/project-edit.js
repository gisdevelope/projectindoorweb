var projectEditModule = angular.module('IndoorApp.editProject', ['ngMaterial', 'ngMessages', 'IndoorApp.projectService']);

//Controller to handle the project edit view
function ProjectController($scope, $mdPanel, projectService) {
    //load md panel
    var mdPanel = $mdPanel;

    // load list of projects when calling controller
    projectService.loadAllProjects();

    // assign loaded project list to scope var
    $scope.projects = projectService.getAllProjects;

    $scope.showProjectInfo = function (projectId) {
        // setup panel position
        var position = mdPanel.newPanelPosition()
            .absolute()
            .center();

        // setup panel config
        var config = {
            attachTo: angular.element(document.body),
            templateUrl: 'pages/panels/project.panel.html',
            hasBackdrop: true,
            panelClass: 'project-dialog',
            position: position,
            controller: ProjectDialogController,
            controllerAs: 'ctrl',
            trapFocus: true,
            zIndex: 150,
            clickOutsideToClose: true,
            escapeToClose: true,
            focusOnOpen: true
        };

        // retrieve project information and display it in a dialog
        projectService.loadProjectInfo(projectId).then(function (data) {
            config.locals = {
                "project": data
            };
            mdPanel.open(config);
        });
    }
}

projectEditModule.controller('ProjectCtrl', ProjectController);


function ProjectDialogController(mdPanelRef, calculationService, dataService, projectService) {
    var panelRef = mdPanelRef;

    // function to load project into calculation Service
    this.loadProject = function () {
        calculationService.loadDataFromProject(this.project);
    };

    this.deleteProject = function (projectId) {
        // call delete and reload projects on success
        dataService.deleteProject(projectId).then(function (data) {
            projectService.loadAllProjects();
        });
        this.closeDialog();
    };

    this.closeDialog = function () {
        panelRef && panelRef.close().then(function () {
            panelRef.destroy();
        });
    };
}

projectEditModule.controller('ProjectDialogCtrl', ProjectDialogController);