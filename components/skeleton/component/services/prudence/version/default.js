
//
// Allows access to the Prudence version as a component attribute.
//

document.require(
	'/sincerity/jvm/',
	'/sincerity/templates/')

var version = Sincerity.JVM.fromProperties(Sincerity.JVM.getResourceAsProperties('com/threecrickets/prudence/version.conf'))

component.context.attributes.put('com.threecrickets.prudence.version', version.version)

if (sincerity.verbosity >= 1) {
	println('Prudence {0} (Restlet {1} {2})'.cast(
		version.version,
		org.restlet.engine.Edition.CURRENT.shortName,
		org.restlet.engine.Engine.VERSION))
}
