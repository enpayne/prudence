//
// Prudence Test Settings
//

document.container.include('defaults/application/settings');

applicationName = 'Prudence Test';
applicationDescription = 'Used to test that Prudence works for you, and useful as a skeleton for creating your own applications';
applicationAuthor = 'Tal Liron';
applicationOwner = 'Three Crickets';
applicationHomeURL = 'http://www.threecrickets.com/prudence/';
applicationContactEmail = 'prudence@threecrickets.com';

hosts = [[component.defaultHost, null], [mysiteHost, null]];

showDebugOnError = true;

preheatResources = ['/data/jython/', '/data/jruby/', '/data/groovy/', '/data/clojure/', '/data/rhino/'];
