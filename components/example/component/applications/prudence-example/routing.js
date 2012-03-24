
app.hosts = {
	'default': '/prudence-example/',
	internal: '/prudence-example/' // If not provided will default to the application subdirectory name
}

app.routes = {
	'/*': [
		'explicit',
		{type: 'filter', library: '/filters/statistics/', next: 'dynamicWeb'},
		[
			{type: 'cacheControl', 'default': 10, mediaTypes: {'text/html': 15}, next: 'staticWeb'},
			{type: 'staticWeb', root: sincerity.container.getLibrariesFile('web')}
		]
	],
	'/person/{id}/': {type: 'implicit', id: 'person', dispatch: 'javascript'},
	'/pythonperson/{id}/': {type: 'implicit', id: 'person', dispatch: 'python'},
	'/groovyperson/{id}/': {type: 'implicit', id: 'person', dispatch: 'groovy'},
	'/phpperson/{id}/': {type: 'implicit', id: 'person', dispatch: 'php'},
	'/rubyperson/{id}/': {type: 'implicit', id: 'person', dispatch: 'ruby'},
	'/clojureperson/{id}/': {type: 'implicit', id: 'person', dispatch: 'clojure'}
}

app.dispatch = {
	javascript: {explicit: '/prudence/dispatch/javascript/', library: '/resources/javascript/'},
	python: {explicit: '/prudence/dispatch/python/', library: '/resources/python/'},
	ruby: {explicit: '/prudence/dispatch/ruby/', library: '/resources/ruby/'},
	groovy: {explicit: '/prudence/dispatch/groovy/', library: '/resources/groovy/'},
	clojure: {explicit: '/prudence/dispatch/clojure/', library: '/resources/clojure/'},
	php: {explicit: '/prudence/dispatch/php/', library: '/resources/php/'}
}

//
// Preheat
//

if (executable.manager.getAdapterByTag('javscript')) {
	app.preheat.push('/scriptlets/javascript/')
	app.preheat.push('/explicit/javascript/')
	app.preheat.push('/person/1/')
}
if (executable.manager.getAdapterByTag('jython')) {
	app.preheat.push('/scriptlets/python/')
	app.preheat.push('/explicit/python/')
	app.preheat.push('/pythonperson/1/')
}
if (executable.manager.getAdapterByTag('groovy')) {
	app.preheat.push('/scriptlets/groovy/')
	app.preheat.push('/explicit/groovy/')
	app.preheat.push('/groovyperson/1/')
}
if (executable.manager.getAdapterByTag('php')) {
	app.preheat.push('/scriptlets/php/')
	app.preheat.push('/explicit/php/')
	app.preheat.push('/phpperson/1/')
}
if (executable.manager.getAdapterByTag('ruby')) {
	app.preheat.push('/scriptlets/ruby/')
	app.preheat.push('/explicit/ruby/')
	app.preheat.push('/rubyperson/1/')
}
if (executable.manager.getAdapterByTag('clojure')) {
	app.preheat.push('/scriptlets/clojure/')
	app.preheat.push('/explicit/clojure/')
	app.preheat.push('/clojureperson/1/')
}
if (executable.manager.getAdapterByTag('velocity')) {
	app.preheat.push('/scriptlets/velocity/')
}