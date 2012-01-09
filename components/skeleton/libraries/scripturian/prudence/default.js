
document.executeOnce('/sincerity/classes/')
document.executeOnce('/sincerity/objects/')
document.executeOnce('/sincerity/templates/')
document.executeOnce('/restlet/')

importClass(com.threecrickets.sincerity.exception.SincerityException)

var Prudence = Prudence || function() {
	/** @exports Public as Prudence */
    var Public = {}
    
    Public.cleanUri = function(uri) {
    	// No doubles
    	uri = uri.replace(/\/\//g, '/')
    	if ((uri == '') || (uri[0] != '/')) {
    		// Always at the beginning
    		uri = '/' + uri
    	}
    	if ((uri != '/') && (uri[uri.length - 1] != '/')) {
    		// Always at the end
    		uri += '/'
    	}
    	return uri
    }

    Public.cleanBaseUri = function(uri) {
    	// No doubles
    	uri = uri.replace(/\/\//g, '/')
    	if ((uri == '') || (uri[0] != '/')) {
    		// Always at the beginning
    		uri = '/' + uri
    	}
    	var length = uri.length
		if ((length > 0) && (uri[length - 1] == '/')) {
			// No trailing slash
			uri = uri.substring(0, length - 1)
		}
    	return uri
    }

	/**
	 * @class
	 * @name Prudence.Application
	 */
    Public.Application = Sincerity.Classes.define(function(Module) {
		/** @exports Public as Prudence.Application */
    	var Public = {}
    	
	    /** @ignore */
	    Public._construct = function() {
    		this.root = Sincerity.Container.here
        	this.settings = {}
        	this.globals = {}
    		this.hosts = {}
        	this.routes = {}
    		this.preheat = {}
    	}

    	Public.create = function(component) {
    		importClass(
    			com.threecrickets.prudence.PrudenceApplication,
    			com.threecrickets.prudence.PrudenceRouter,
    			com.threecrickets.prudence.ApplicationTaskCollector,
    			com.threecrickets.prudence.DelegatedStatusService,
    			com.threecrickets.prudence.util.LoggingUtil,
    			com.threecrickets.prudence.util.PreheatTask,
    			org.restlet.resource.Finder,
    			org.restlet.routing.Router,
    			org.restlet.routing.Template,
    			org.restlet.routing.Redirector,
    			org.restlet.data.Reference,
    			org.restlet.data.MediaType,
				java.util.concurrent.CopyOnWriteArrayList,
    			java.io.File)

    		// Ensure settings exist
    		this.settings.description = Sincerity.Objects.ensure(this.settings.description, {})
    		this.settings.errors = Sincerity.Objects.ensure(this.settings.errors, {})
    		this.settings.code = Sincerity.Objects.ensure(this.settings.code, {})
    		this.settings.mediaTypes = Sincerity.Objects.ensure(this.settings.mediaTypes, {})

    		// Sensible default settings
			this.settings.code.minimumTimeBetweenValidityChecks = Sincerity.Objects.ensure(this.settings.code.minimumTimeBetweenValidityChecks, 1000)
			this.settings.code.defaultDocumentName = Sincerity.Objects.ensure(this.settings.code.defaultDocumentName, 'default')
			this.settings.code.defaultExtension = Sincerity.Objects.ensure(this.settings.code.defaultExtension, 'js')
			this.settings.code.defaultLanguageTag = Sincerity.Objects.ensure(this.settings.code.defaultLanguageTag, 'javascript')
    		this.settings.logger = Sincerity.Objects.ensure(this.settings.logger, this.root.name)

    		this.component = component
    		this.context = component.context.createChildContext()
        	this.instance = new PrudenceApplication(this.context)
    		
    		// Logger
    		this.context.logger = LoggingUtil.getRestletLogger(this.settings.logger)
    		
    		// Status service
    		this.instance.statusService = new DelegatedStatusService(this.settings.code.sourceViewable ? '/source/' : null)
			this.instance.statusService.debugging = true == this.settings.errors.debug
			if (Sincerity.Objects.exists(this.settings.errors.homeUrl)) {
				println('Home URL: "{0}"'.cast(this.settings.errors.homeUrl))
				this.instance.statusService.homeRef = new Reference(this.settings.errors.homeUrl)
			}
			if (Sincerity.Objects.exists(this.settings.errors.contactEmail)) {
				this.instance.statusService.contactEmail = this.settings.errors.contactEmail
			}

    		// Description
			if (Sincerity.Objects.exists(this.settings.description.name)) {
				this.instance.name = this.settings.description.name
			}
			if (Sincerity.Objects.exists(this.settings.description.description)) {
				this.instance.description = this.settings.description.description
			}
			if (Sincerity.Objects.exists(this.settings.description.author)) {
				this.instance.author = this.settings.description.author
			}
			if (Sincerity.Objects.exists(this.settings.description.owner)) {
				this.instance.owner = this.settings.description.owner
			}

    		// Media types
			for (var extension in this.settings.mediaTypes) {
				var type = this.settings.mediaTypes[extension]
				if (Sincerity.Objects.isString(type)) {
					type = MediaType.valueOf(type)
				}
				this.instance.metadataService.addExtension(extension, type)
			}

			// Trailing-slash redirector
		    var addTrailingSlashRedirector = new Redirector(this.context, '{ri}/', Redirector.MODE_CLIENT_PERMANENT)

    		// Default internal host to subdirectory name
    		if (!Sincerity.Objects.exists(this.hosts.internal)) {
    			this.hosts.internal = this.root.name
    		}
    		
        	// Attach to hosts
        	for (var name in this.hosts) {
        		var host = Restlet.getHost(component, name)
        		if (!Sincerity.Objects.exists(host)) {
        			throw new SavoryException('Unknown host: ' + name)
        		}
        		var uri = Module.cleanBaseUri(this.hosts[name])
        		println('Attaching application to "{0}" on host "{1}"'.cast(uri, host))
        		if (uri != '') {
        			host.attach(uri, addTrailingSlashRedirector).matchingMode = Template.MODE_EQUALS
        		}
        		host.attach(uri, this.instance)
        	}

        	// Inbound root
        	this.instance.inboundRoot = new PrudenceRouter(this.context)
        	this.instance.inboundRoot.routingMode = Router.MODE_BEST_MATCH
        	
        	// Libraries
			this.libraryDocumentSources = new CopyOnWriteArrayList()

        	// Container library
        	var containerLibraryDocumentSource = component.context.attributes.get('prudence.containerLibraryDocumentSource')
        	if (!Sincerity.Objects.exists(containerLibraryDocumentSource)) {
	    		var library = sincerity.container.getLibrariesFile('scripturian')
				containerLibraryDocumentSource = this.createDocumentSource(library)
	    		var existing = component.context.attributes.put('prudence.containerLibraryDocumentSource', containerLibraryDocumentSource)
	    		if (Sincerity.Objects.exists(existing)) {
	    			containerLibraryDocumentSource = existing
	    		}
        	}

        	if (Sincerity.Objects.exists(this.settings.code.libraries)) {
    			for (var i in this.settings.code.libraries) {
    				var library = this.settings.code.libraries[i]
    				
    	    		if (!(library instanceof File)) {
    	    			library = new File(this.root, library).absoluteFile
    	    		}
    				
    				println('Adding library: "{0}"'.cast(library))
    				var documentSource = this.createDocumentSource(library)
    				this.libraryDocumentSources.add(documentSource)
    				
    				if (i == 0) {
    					// We'll use the first library for handlers and tasks
    					var extraDocumentSources = new CopyOnWriteArrayList()
    					extraDocumentSources.add(containerLibraryDocumentSource)
    					
    					// Handlers
						this.globals['com.threecrickets.prudence.DelegatedHandler'] = {
							documentSource: documentSource,
							extraDocumentSources: extraDocumentSources,
				    		libraryDocumentSources: this.libraryDocumentSources,
				    		defaultName: this.settings.code.defaultDocumentName,
				    		defaultLanguageTag: this.settings.code.defaultLanguageTag,
				    		languageManager: executable.manager,
				    		sourceViewable: this.settings.code.sourceViewable
						}
						println('Handlers: "{0}"'.cast(library))

    					// Tasks
						this.globals['com.threecrickets.prudence.ApplicationTask'] = {
							documentSource: documentSource,
							extraDocumentSources: extraDocumentSources,
				    		libraryDocumentSources: this.libraryDocumentSources,
				    		defaultName: this.settings.code.defaultDocumentName,
				    		defaultLanguageTag: this.settings.code.defaultLanguageTag,
				    		languageManager: executable.manager,
				    		sourceViewable: this.settings.code.sourceViewable
						}
						println('Tasks: "{0}"'.cast(library))
    				}
    			}
        	}
        	
			println('Adding library: "{0}"'.cast(containerLibraryDocumentSource.basePath))
			this.libraryDocumentSources.add(containerLibraryDocumentSource)

        	// Sincerity library
        	var sincerityLibraryDocumentSource = component.context.attributes.get('prudence.sincerityLibraryDocumentSource')
        	if (!Sincerity.Objects.exists(sincerityLibraryDocumentSource)) {
	    		var library = sincerity.getHomeFile('libraries', 'scripturian')
				sincerityLibraryDocumentSource = this.createDocumentSource(library)
	    		var existing = component.context.attributes.put('prudence.sincerityLibraryDocumentSource', sincerityLibraryDocumentSource)
	    		if (Sincerity.Objects.exists(existing)) {
	    			sincerityLibraryDocumentSource = existing
	    		}
        	}
			println('Adding library: "{0}"'.cast(sincerityLibraryDocumentSource.basePath))
			this.libraryDocumentSources.add(sincerityLibraryDocumentSource)

    		// Source viewer
    		if (true == this.settings.code.sourceViewable) {
    			this.sourceViewableDocumentSources = new CopyOnWriteArrayList()
				this.globals['com.threecrickets.prudence.SourceCodeResource.documentSources'] = this.sourceViewableDocumentSources
				var sourceViewer = new Finder(this.context, Sincerity.Container.getClass('com.threecrickets.prudence.SourceCodeResource'))
				this.instance.inboundRoot.attach('/source/', sourceViewer).matchingMode = Template.MODE_EQUALS
				println('Attaching "/source/" to "{0}"'.cast(sourceViewer))
    		}

        	// Create and attach restlets
        	for (var uri in this.routes) {
        		var restlet = this.routes[uri]

        		var attachBase = false
        		var length = uri.length
        		if (length > 1) {
        			var last = uri[length - 1]
	        		if (last == '*') {
	        			uri = uri.substring(0, length - 1)
	        			attachBase = true
	        		}
        		}
        		
        		uri = Module.cleanUri(uri)

        		restlet = this.createRestlet(restlet, uri)
        		if (Sincerity.Objects.exists(restlet)) {
	        		if (attachBase) {
	            		println('Attaching "{0}*" to {1}'.cast(uri, restlet))
	        			this.instance.inboundRoot.attachBase(uri, restlet)
	        		}
	        		else {
	            		println('Attaching "{0}" to {1}'.cast(uri, restlet))
	        			this.instance.inboundRoot.attach(uri, restlet).matchingMode = Template.MODE_EQUALS
	        		}
        		}
        	}

			// crontab
			var crontab = new File(this.root, 'crontab').absoluteFile
			if (crontab.exists() && !crontab.directory) {
				println('Adding crontab: "{0}"'.cast(crontab))
				var scheduler = component.context.attributes.get('com.threecrickets.prudence.scheduler')
				scheduler.addTaskCollector(new ApplicationTaskCollector(crontab, this.instance))
			}

			// Use common cache, if exists
			var cache = component.context.attributes.get('com.threecrickets.prudence.cache')
			if (Sincerity.Objects.exists(cache)) {
				this.globals['com.threecrickets.prudence.cache'] = cache
			}

			// Allow access to component
			// (This can be considered a security breach, because it allows applications to access other applications)
			this.globals['com.threecrickets.prudence.component'] = component
			
			// Apply globals
        	var globals = Sincerity.Objects.flatten(this.globals)
        	for (var name in globals) {
        		this.context.attributes.put(name, globals[name])
        	}
			
			// Preheat tasks
			var internal = this.hosts.internal.replace(/\//g, '')
			for (var p in this.preheat) {
				var uri = this.preheat[p]
				executorTasks.push(new PreheatTask(internal, uri, this.instance, this.settings.logger))
			}

			// Add to application list
			var applications = component.context.attributes.get('com.threecrickets.prudence.applications')
			if (!Sincerity.Objects.exists(applications)) {
				applications = new CopyOnWriteArrayList()
				var existing = component.context.attributes.putIfAbsent('com.threecrickets.prudence.applications', applications)
				if (Sincerity.Objects.exists(existing)) {
					applications = existing
				}
			}
			applications.add(this.instance)

        	return this.instance
    	}
    	
    	Public.createRestlet = function(restlet, uri) {
    		if (Sincerity.Objects.isArray(restlet)) {
    			return new Module.Chain({restlets: restlet}).create(this, uri)
    		}
    		else if (Sincerity.Objects.isString(restlet)) {
    			if (restlet == 'hidden') {
            		println('Hiding "{0}"'.cast(uri))
    				this.instance.inboundRoot.hide(uri)
    				return null
    			}
    			else if (restlet[0] == '/') {
    				return new Module.Capture({uri: restlet}).create(this, uri)
    			}
    			else {
        			var type = Module[Sincerity.Objects.capitalize(restlet)]
    				if (Sincerity.Objects.exists(type)) {
    					return new type().create(this, uri)
    				}
    				else {
    					return new Module.Implicit({id: restlet}).create(this, uri)
    				}
    			}
    		}
    		else if (Sincerity.Objects.isString(restlet.type)) {
    			var type = Module[Sincerity.Objects.capitalize(restlet.type)]
    			delete restlet.type
    			return new type(restlet).create(this, uri)        			
    		}
    		else {
    			return restlet.create(this, uri)
    		}
    	}
    	
    	Public.createDocumentSource = function(root, preExtension, defaultDocumentName, defaultExtension) {
    		importClass(
    			com.threecrickets.scripturian.document.DocumentFileSource)

        	return new DocumentFileSource(
				'container/' + sincerity.container.getRelativePath(root) + '/',
				root,
				Sincerity.Objects.ensure(defaultDocumentName, this.settings.code.defaultDocumentName),
				Sincerity.Objects.ensure(defaultExtension, this.settings.code.defaultExtension),
				Sincerity.Objects.ensure(preExtension, null),
				this.settings.code.minimumTimeBetweenValidityChecks
			)
    	}
    	
    	Public.defrost = function(documentSource) {
    		importClass(
    			com.threecrickets.scripturian.util.DefrostTask)
    			
    		if (true == this.settings.code.defrost) {
				var tasks = DefrostTask.forDocumentSource(documentSource, executable.manager, this.settings.code.defaultLanguageTag, false, true)
				for (var t in tasks) {
					executorTasks.push(tasks[t])
				}
    		}
    	}
    	
    	return Public    
    }(Public))

	/**
	 * @class
	 * @name Prudence.Restlet
	 */
    Public.Resource = Sincerity.Classes.define(function() {
		/** @exports Public as Prudence.Restlet */
    	var Public = {}
    	
    	Public.create = function(app, uri) {
    	}
    	
    	return Public
    }())

	/**
	 * @class
	 * @name Prudence.StaticWeb
	 * @augments Prudence.Restlet
	 */
    Public.StaticWeb = Sincerity.Classes.define(function(Module) {
		/** @exports Public as Prudence.StaticWeb */
    	var Public = {}
    	
	    /** @ignore */
    	Public._inherit = Module.Restlet

		/** @ignore */
    	Public._configure = ['root', 'listingAllowed', 'negotiate', 'compress']

    	Public.create = function(app, uri) {
    		importClass(
    			org.restlet.resource.Directory,
    			org.restlet.engine.application.Encoder,
    			java.io.File)
    		
    		this.root = Sincerity.Objects.ensure(this.root, 'mapped')
    		if (!(this.root instanceof File)) {
    			this.root = new File(app.root, this.root).absoluteFile
    		}
    		
    		var directory = new Directory(app.context, this.root.toURI())
    		directory.listingAllowed = Sincerity.Objects.ensure(this.listingAllowed, false)
    		directory.negotiatingContent = Sincerity.Objects.ensure(this.negotiate, true)
    		
    		if (Sincerity.Objects.ensure(this.compress, true)) {
    			var encoder = new Encoder(app.context)
    			encoder.next = directory
    			directory = encoder
    		}
    		
    		return directory
    	}
    	
    	return Public
    }(Public))

	/**
	 * @class
	 * @name Prudence.DynamicWeb
	 * @augments Prudence.Restlet
	 */
    Public.DynamicWeb = Sincerity.Classes.define(function(Module) {
		/** @exports Public as Prudence.DynamicWeb */
    	var Public = {}
    	
	    /** @ignore */
    	Public._inherit = Module.Restlet

		/** @ignore */
    	Public._configure = ['root', 'fragmentsRoot', 'passThroughs', 'preExtension', 'defaultDocumentName', 'defaultExtension', 'clientCachingMode']

    	Public.create = function(app, uri) {
    		if (Sincerity.Objects.exists(app.globals['com.threecrickets.prudence.GeneratedTextResource'])) {
    			throw new SincerityException('There can be only one DynamicWeb per application')
    		}

    		importClass(
    			com.threecrickets.prudence.util.PhpExecutionController,
    			org.restlet.resource.Finder,
    			java.util.concurrent.CopyOnWriteArrayList,
    			java.util.concurrent.CopyOnWriteArraySet,
    			java.util.concurrent.ConcurrentHashMap,
    			java.io.File)
    			
    		this.root = Sincerity.Objects.ensure(this.root, 'mapped')
    		if (!(this.root instanceof File)) {
    			this.root = new File(app.root, this.root).absoluteFile
    		}
    		
    		println('DynamicWeb at "{0}"'.cast(this.root))
    		
    		this.fragmentsRoot = Sincerity.Objects.ensure(this.fragmentsRoot, 'fragments')
    		if (!(this.fragmentsRoot instanceof File)) {
    			this.fragmentsRoot = new File(app.root, this.fragmentsRoot).absoluteFile
    		}

    		if (Sincerity.Objects.isString(this.clientCachingMode)) {
	    		if (this.clientCachingMode == 'disabled') {
	    			this.clientCachingMode = 0
	    		}
	    		else if (this.clientCachingMode == 'conditional') {
	    			this.clientCachingMode = 1
	    		}
	    		else if (this.clientCachingMode == 'offline') {
	    			this.clientCachingMode = 2
	    		}
	    		else {
        			throw new SavoryException('Unsupported clientCachingMode: ' + this.clientCachingMode)
	    		}
    		}
    		else if (!Sincerity.Objects.exists(this.clientCachingMode)) {
    			this.clientCachingMode = 1
    		}

    		this.defaultDocumentName = Sincerity.Objects.ensure(this.defaultDocumentName, 'index')
    		this.defaultExtension = Sincerity.Objects.ensure(this.defaultExtension, 'html')
			this.preExtension = Sincerity.Objects.ensure(this.preExtension, 'd')

    		var generatedTextResource = app.globals['com.threecrickets.prudence.GeneratedTextResource'] = {
    			documentSource: app.createDocumentSource(this.root, this.preExtension, this.defaultDocumentName, this.defaultExtenion),
	    		extraDocumentSources: new CopyOnWriteArrayList(),
	    		libraryDocumentSources: app.libraryDocumentSources,
	    		passThroughDocuments: new CopyOnWriteArraySet(),
	    		cacheKeyPatternHandlers: new ConcurrentHashMap(),
	    		scriptletPlugins: new ConcurrentHashMap(),
	    		clientCachingMode: this.clientCachingMode,
	    		defaultIncludedName: this.defaultDocumentName,
	    		executionController: new PhpExecutionController(), // Adds PHP predefined variables
    			languageManager: executable.manager,
	    		sourceViewable: app.settings.code.sourceViewable
    		}

    		// Fragments
    		if (Sincerity.Objects.exists(this.fragmentsRoot)) {
    			generatedTextResource.extraDocumentSources.add(app.createDocumentSource(this.fragmentsRoot, null, this.defaultDocumentName, this.defaultExtenion))
    		}

        	// Common fragments
        	var commonFragmentsDocumentSource = app.component.context.attributes.get('prudence.fragmentsDocumentSource')
        	if (!Sincerity.Objects.exists(commonFragmentsDocumentSource)) {
	    		var library = sincerity.container.getFile('component', 'fragments')
				commonFragmentsDocumentSource = app.createDocumentSource(library, null, this.defaultDocumentName, this.defaultExtenion)
	    		app.component.context.attributes.put('prudence.fragmentsDocumentSource', commonFragmentsDocumentSource)
        	}

        	generatedTextResource.extraDocumentSources.add(commonFragmentsDocumentSource)

        	// Viewable source
        	if (true == app.settings.code.sourceViewable) {
    			app.sourceViewableDocumentSources.add(generatedTextResource.documentSource)
    			app.sourceViewableDocumentSources.addAll(generatedTextResource.extraDocumentSources)
        	}
    		
        	// Pass-throughs
    		if (Sincerity.Objects.exists(this.passThroughs)) {
	    		for (var i in this.passThroughs) {
	    			println('Pass through: "{0}"'.cast(this.passThroughs[i]))
	    			generatedTextResource.passThroughDocuments.add(this.passThroughs[i])
	    		}
    		}
    		
    		// Defrost
    		app.defrost(generatedTextResource.documentSource)
    		
    		return new Finder(app.context, Sincerity.Container.getClass('com.threecrickets.prudence.GeneratedTextResource'))
    	}
    	
    	return Public
    }(Public))

	/**
	 * @class
	 * @name Prudence.Explicit
	 * @augments Prudence.Restlet
	 */
    Public.Explicit = Sincerity.Classes.define(function(Module) {
		/** @exports Public as Prudence.Explicit */
    	var Public = {}
    	
	    /** @ignore */
    	Public._inherit = Module.Restlet

		/** @ignore */
    	Public._configure = ['root', 'passThroughs', 'implicit', 'preExtension']

    	Public.create = function(app, uri) {
    		if (Sincerity.Objects.exists(app.globals['com.threecrickets.prudence.DelegatedResource'])) {
    			throw new SincerityException('There can be only one Explicit per application')
    		}

    		importClass(
    			org.restlet.resource.Finder,
    			java.util.concurrent.CopyOnWriteArraySet,
    			java.io.File)

    		this.root = Sincerity.Objects.ensure(this.root, 'mapped')
    		if (!(this.root instanceof File)) {
    			this.root = new File(app.root, this.root).absoluteFile
    		}

    		this.preExtension = Sincerity.Objects.ensure(this.preExtension, 'e')
    		
    		app.implicit = this.implicit = Sincerity.Objects.ensure(this.implicit, {})
    		this.implicit.routerDocumentName = Sincerity.Objects.ensure(this.implicit.routerDocumentName, '/prudence/implicit/')

    		var delegatedResource = app.globals['com.threecrickets.prudence.DelegatedResource'] = {
    			documentSource: app.createDocumentSource(this.root, this.preExtension),
	    		libraryDocumentSources: app.libraryDocumentSources,
	    		passThroughDocuments: new CopyOnWriteArraySet(),
	    		defaultName: app.settings.code.defaultDocumentName,
	    		defaultLanguageTag: app.settings.code.defaultLanguageTag,
	    		languageManager: executable.manager,
	    		sourceViewable: app.settings.code.sourceViewable
    		}

        	// Pass-throughs
    		if (Sincerity.Objects.exists(this.passThroughs)) {
	    		for (var i in this.passThroughs) {
	    			println('Pass through: "{0}"'.cast(this.passThroughs[i]))
	    			delegatedResource.passThroughDocuments.add(this.passThroughs[i])
	    		}
    		}

        	// Viewable source
        	if (true == app.settings.code.sourceViewable) {
    			app.sourceViewableDocumentSources.add(delegatedResource.documentSource)
    			app.sourceViewableDocumentSources.addAll(app.libraryDocumentSources)
        	}

    		// Implicit router
    		delegatedResource.passThroughDocuments.add(this.implicit.routerDocumentName)
       		app.implicit.routerUri = Module.cleanUri(uri + this.implicit.routerDocumentName)
    		app.instance.inboundRoot.hide(app.implicit.routerUri)

    		// Defrost
    		app.defrost(delegatedResource.documentSource)

    		return new Finder(app.context, Sincerity.Container.getClass('com.threecrickets.prudence.DelegatedResource'))
    	}
    	
    	return Public
    }(Public))

	/**
	 * @class
	 * @name Prudence.Implicit
	 * @augments Prudence.Restlet
	 */
    Public.Implicit = Sincerity.Classes.define(function(Module) {
		/** @exports Public as Prudence.Implicit */
    	var Public = {}
    	
	    /** @ignore */
    	Public._inherit = Module.Restlet

		/** @ignore */
    	Public._configure = ['id', 'locals']

    	Public.create = function(app, uri) {
    		importClass(
    			com.threecrickets.prudence.util.Injector,
    			com.threecrickets.prudence.util.CapturingRedirector)
    			
       		if (!Sincerity.Objects.exists(app.implicit)) {
    			throw new SincerityException('An Explicit must be attached before an Implicit can be created')
       		}
    		
    		app.implicit.resourcesDocumentName = Sincerity.Objects.ensure(app.implicit.resourcesDocumentName, '/resources/')

       		var capture = new CapturingRedirector(app.context, 'riap://application' + app.implicit.routerUri + '?{rq}', false)
    		var injector = new Injector(app.context, capture)
    		injector.values.put('prudence.id', this.id)

    		// Extra locals
    		if (Sincerity.Objects.exists(this.locals)) {
    			for (var i in this.locals) {
    				injector.values.put(i, this.locals[i])
    			}
    		}
   
        	app.globals['prudence.implicit.resourcesDocumentName'] = app.implicit.resourcesDocumentName

    		return injector
    	}
    	
    	return Public
    }(Public))

	/**
	 * @class
	 * @name Prudence.Capture
	 * @augments Prudence.Restlet
	 */
    Public.Capture = Sincerity.Classes.define(function(Module) {
		/** @exports Public as Prudence.Capture */
    	var Public = {}
    	
	    /** @ignore */
    	Public._inherit = Module.Restlet

		/** @ignore */
    	Public._configure = ['uri', 'hidden', 'locals']

    	Public.create = function(app, uri) {
    		importClass(
    			com.threecrickets.prudence.util.Injector,
    			com.threecrickets.prudence.util.CapturingRedirector)
    			
       		var capture = new CapturingRedirector(app.context, 'riap://application' + this.uri + '?{rq}', false)

    		if (Sincerity.Objects.exists(this.locals)) {
        		var injector = new Injector(app.context, capture)

        		for (var i in this.locals) {
    				injector.values.put(i, this.locals[i])
    			}
        		
        		capture = injector
    		}
    		
    		if (true == this.hidden) {
    			app.instance.inboundRoot.hide(uri)    			
    		}
   
    		return capture
    	}
    	
    	return Public
    }(Public))

    /**
	 * @class
	 * @name Prudence.Chain
	 * @augments Prudence.Restlet 
	 */
    Public.Chain = Sincerity.Classes.define(function(Module) {
		/** @exports Public as Prudence.Chain */
    	var Public = {}
    	
	    /** @ignore */
    	Public._inherit = Module.Restlet

		/** @ignore */
    	Public._configure = ['restlets']

    	Public.create = function(app, uri) {
    		importClass(com.threecrickets.prudence.util.Fallback)
    		
    		var fallback = new Fallback(app.context, app.settings.code.minimumTimeBetweenValidityChecks)
    		
    		if (Sincerity.Objects.exists(this.restlets)) {
	    		for (var i in this.restlets) {
	    			var restlet = app.createRestlet(this.restlets[i], uri)
	    			if (Sincerity.Objects.exists(restlet)) {
	    				fallback.addTarget(restlet)    				
	    			}
	    		}
    		}
    		
    		return fallback
    	}
    	
    	return Public
    }(Public))
    
    /**
	 * @class
	 * @name Prudence.Filter
	 * @augments Prudence.Restlet 
	 */
    Public.Filter = Sincerity.Classes.define(function(Module) {
		/** @exports Public as Prudence.Filter */
    	var Public = {}
    	
	    /** @ignore */
    	Public._inherit = Module.Restlet

		/** @ignore */
    	Public._configure = ['library', 'next']

    	Public.create = function(app, uri) {
    		importClass(com.threecrickets.prudence.DelegatedFilter)
    		
    		this.next = app.createRestlet(this.next, uri)
    		var filter = new DelegatedFilter(app.context, this.next, this.library)
    		
    		return filter
    	}
    	
    	return Public
    }(Public))
    
    return Public
}()