//
// Stickstick Settings
//

document.execute('defaults/application/settings/');

applicationName = 'Stickstick';
applicationDescription = 'Share online sticky notes';
applicationAuthor = 'Tal Liron';
applicationOwner = 'Three Crickets';
applicationHomeURL = 'http://threecrickets.com/prudence/stickstick/';
applicationContactEmail = 'prudence@threecrickets.com';

runtimeAttributes['stickstick.backend'] = 'h2';
runtimeAttributes['stickstick.username'] = 'root';
runtimeAttributes['stickstick.password'] = 'root';
runtimeAttributes['stickstick.host'] = '';
runtimeAttributes['stickstick.database'] = 'data/stickstick/stickstick';

showDebugOnError = true;

preheatResources = ['data/'];
