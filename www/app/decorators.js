/**
* Created by kevin on 07/11/2015 for Podcast Server
*/

import angular from 'angular';

export function Module({name, inject, modules = []}) {
    return Target => {

        if (angular.isDefined(name) && angular.isDefined(inject))
            throw new TypeError ("Name and Inject can't be define in the same @Module");

        Target.$angularModule = angular.isUndefined(inject) ? angular.module(name, modules.map(extractAngularModuleName)) : extractAngularModule(inject);

        if (Target.component) Target.$angularModule.directive(Target.$componentName, Target.component);
        if (Target.routeConfig) Target.$angularModule.config(Target.routeConfig);
        if (Target.$serviceName) Target.$angularModule.service(Target.$serviceName, Target);

        for (let config of Target.$config || []) {
            Target.$angularModule.config(config);
        }

        for (let constant of Target.$constant || []) {
            Target.$angularModule.constant(constant.name, constant.value);
        }

        for (let run of Target.$run || []) {
            Target.$angularModule.run(run);
        }

    };
}

export function RouteConfig({ path, as = 'vm', reloadOnSearch = true, resolve = {}}) {
    return Target => {
        if (!Target.$template) throw new TypeError("Template should be defined");
        if (!path) throw new TypeError("Path should be Defined");

        Target.routeConfig = ($routeProvider) => {
            "ngInject";

            let parameters = {
                template: Target.$template,
                controller: Target,
                controllerAs : as,
                reloadOnSearch : reloadOnSearch,
                resolve : resolve
            };

            Target.$hotKeys && (parameters.hotkeys = Target.$hotKeys);

            $routeProvider.when(path, parameters);
        };
    };
}

export function HotKeys({hotKeys = []}) {
    return Target =>  {
        Target.$hotKeys = hotKeys;
    };
}

export function Component({restrict = 'E', scope = true, as = 'vm', bindToController = true, replace = false, transclude = false, selector}) {
    return Target => {
        if (!Target.$template && restrict.indexOf('E') !== -1 ) throw new TypeError("A Template should be defined with the annotation @View for Element Component (restrict : E)");
        if (!selector) throw new TypeError("A selector should be defined in the current annotation @Component");

        Target.$componentName = snakeCaseToCamelCase(selector);
        Target.component = () => {
            let ddo = {
                restrict : restrict,
                transclude : transclude,
                replace : replace,
                template: Target.$template,
                scope : scope,
                controller : Target,
                controllerAs : as,
                bindToController : bindToController
            };

            Target.link && (ddo.link = Target.link);

            return ddo;
        };
    };
}

export function View({template}) {
    return Target => {
        Target.$template = template;
    };
}

export function Service(name) {
    return Target => {
        Target.$serviceName = name;
    };
}

export function Config(configFunction) {
    return Target => {
        if (!Target.$config) Target.$config = [];
        Target.$config.push(configFunction);
    };
}

export function Run(runFunction) {
    return Target => {
        if (!Target.$run) Target.$run = [];
        Target.$run.push(runFunction);
    };
}

export function Boot({ element = document, strictDi = false}) {
    return Target => {
        if (!angular.isDefined(Target.$angularModule))
            throw new TypeError ("@Boot should be used only on a @Module Class");

        angular.element(document).ready(() =>  angular.bootstrap(element, [ Target.$angularModule.name ], { strictDi: strictDi }));
    };
}

export function Constant({ name, value}) {
    return Target => {

        if (!angular.isDefined(name) || !angular.isDefined(value))
            throw new TypeError ("Name and value should be defined for @Constant");

        if (!Target.$constant) Target.$constant = [];
        Target.$constant.push({name : name, value : value});
    };
}

export function UibModal({animation = true, backdrop = true, bindToController = true, as = 'vm', keyboard = true, resolve, size}) {
    return Target => {
        let $UibModalConf = {
            animation : animation,
            backdrop : backdrop,
            bindToController: bindToController,
            controller : Target,
            controllerAs : as,
            keyboard : keyboard,
            resolve : resolve,
            size : size,
            template : Target.$template
        };

        Target.$UibModalConf = {
            asDefault : () => $UibModalConf,
            withResolve : (resolve) => angular.extend({}, $UibModalConf, {resolve : angular.extend({}, $UibModalConf.resolve, resolve) })
        };
    };
}

function snakeCaseToCamelCase(string) {
    return string.replace( /-([a-z])/ig, (_,letter) => letter.toUpperCase());
}

function extractAngularModuleName(clazz) {
    if (clazz.$angularModule)
        return clazz.$angularModule.name;

    return clazz.name ? clazz.name : clazz;
}

function extractAngularModule(clazz) {
    return clazz.$angularModule ? clazz.$angularModule : clazz;
}
