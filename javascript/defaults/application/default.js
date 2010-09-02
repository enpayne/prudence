//
// Prudence Application
//

importClass(
	org.restlet.data.Reference,
	org.restlet.data.MediaType,
	com.threecrickets.prudence.DelegatedStatusService,
	com.threecrickets.prudence.ApplicationTaskCollector)

//
// Settings
//

executeOrDefault(applicationBasePath + '/settings/', 'defaults/application/settings/')

//
// Application
//

executeOrDefault(applicationBasePath + '/application/', 'defaults/application/application/')

applicationInstance.name = applicationName
applicationInstance.description = applicationDescription
applicationInstance.author = applicationAuthor
applicationInstance.owner = applicationOwner

//
// StatusService
//

applicationInstance.statusService = new DelegatedStatusService(showDebugOnError ? showSourceCodeURL : null)
applicationInstance.statusService.debugging = showDebugOnError
applicationInstance.statusService.homeRef = new Reference(applicationHomeURL)
applicationInstance.statusService.contactEmail = applicationContactEmail

//
// MetaData
//

applicationInstance.metadataService.addExtension('php', MediaType.TEXT_HTML)

//
// Routing
//

executeOrDefault(applicationBasePath + '/routing/', 'defaults/application/routing/')

//
// Logging
//

applicationInstance.context.setLogger(applicationLoggerName)

//
// Predefined Globals
//

for(var key in predefinedGlobals) {
	applicationGlobals.put(key, predefinedGlobals[key])
}

//
// Tasks
//

var tasksDocumentSource = new DocumentFileSource(applicationBasePath + tasksBasePath, tasksDefaultName, 'js', tasksMinimumTimeBetweenValidityChecks)
applicationGlobals.put('com.threecrickets.prudence.ApplicationTask.languageManager', languageManager)
applicationGlobals.put('com.threecrickets.prudence.ApplicationTask.defaultLanguageTag', 'javascript')
applicationGlobals.put('com.threecrickets.prudence.ApplicationTask.defaultName', tasksDefaultName)
applicationGlobals.put('com.threecrickets.prudence.ApplicationTask.documentSource', tasksDocumentSource)
scheduler.addTaskCollector(new ApplicationTaskCollector(new File(applicationBasePath + '/crontab'), applicationInstance))
