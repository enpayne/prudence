//
// This script implements and handles a REST resource. Simply put, it is a state,
// addressed by a URL, that responds to verbs. Verbs represent logical operations
// on the state, such as create, read, update and delete (CRUD). They are primitive
// communications, which include very minimal session and no transaction state. As such,
// they are very straightforward to implement, and can lead to very scalable
// applications. 
//
// The exact URL of this resource depends on its its filename and/or its location in
// your directory structure. See your settings.js for more information.
//

importClass(
	java.util.concurrent.locks.ReentrantReadWriteLock,
	org.restlet.ext.json.JsonRepresentation);

// Include the context library
document.container.include('../libraries/rhino/context');

// Include the JSON library
document.container.include('../libraries/rhino/json2');

// State
//
// These make sure that our state is properly stored in the context,
// so that we always use the same state, even if this script is recompiled.

function getStateLock() {
	return getContextAttribute('rhino.stateLock', function() {
		return new ReentrantReadWriteLock();
	});
}

function getState() {
	return getContextAttribute('rhino.state', function() {
		return {'name': 'Coraline', 'media': 'Film', 'rating': 'A+', 'characters': ['Coraline', 'Wybie', 'Mom', 'Dad']};
	});
}

function setState(value) {
	document.container.resource.context.attributes.put('rhino.state', value); 
}

// This function is called when the resource is initialized. We will use it to set
// general characteristics for the resource.

function handleInit() {
	// The order in which we add the variants is their order of preference.
	// Note that clients often include a wildcard (such as "*/*") in the
	// "Accept" attribute of their request header, specifying that any media type
	// will do, in which case the first one we add will be used.
	
    document.container.addMediaTypeByName('application/json');
    document.container.addMediaTypeByName('text/plain');
}

// This function is called for the GET verb, which is expected to behave as a
// logical "read" of the resource's state.
//
// The expectation is that it return one representation, out of possibly many, of the
// resource's state. Returned values can be of any explicit sub-class of
// org.restlet.resource.Representation. Other types will be automatically converted to
// string representation using the client's requested media type and character set.
// These, and the language of the representation (defaulting to null), can be read and
// changed via document.container.mediaType, document.container.characterSet, and
// document.container.language.
//
// Additionally, you can use document.container.variant to interrogate the client's provided
// list of supported languages and encoding.

function handleGet() {
	var r;
	var stateLock = getStateLock();
	var state = getState();
	
	stateLock.readLock().lock();
	try {
		r = JSON.stringify(state);
	}
	finally {
		stateLock.readLock().unlock();
	}
	
	// Return a representation appropriate for the requested media type
	// of the possible options we created in handleInit()

	if(document.container.mediaTypeName == 'application/json') {
		r = new JsonRepresentation(r);
	}
	
	return r;
}

// This function is called for the POST verb, which is expected to behave as a
// logical "update" of the resource's state.
//
// The expectation is that document.container.entity represents an update to the state,
// that will affect future calls to handleGet(). As such, it may be possible
// to accept logically partial representations of the state.
//
// You may optionally return a representation, in the same way as handleGet().
// Because JavaScript functions return the last statement's value by default,
// you must explicitly return a null if you do not want to return a representation
// to the client.

function handlePost() {
	// Note that we are using the JSON library to parse the entity. While
	// a simple eval() would also work, JSON.parse() is much safer.
	// Note, too, that we are using String() to translate the Java string
	// into a JavaScript string. In many cases this is unnecessary, but
	// in this case the JSON library specifically expects a JavaScript string
	// object.
	
	var update = JSON.parse(String(document.container.entity.text));
	var stateLock = getStateLock();
	var state = getState();
	
	stateLock.writeLock().lock();
	try {
		for(var key in update) {
			state[key] = update[key];
		}
	}
	finally {
		stateLock.writeLock().unlock();
	}
	
	return handleGet();
}

// This function is called for the PUT verb, which is expected to behave as a
// logical "create" of the resource's state.
//
// The expectation is that document.container.entity represents an entirely new state,
// that will affect future calls to handleGet(). Unlike handlePost(),
// it is expected that the representation be logically complete.
//
// You may optionally return a representation, in the same way as handleGet().
// Because JavaScript functions return the last statement's value by default,
// you must explicitly return a null if you do not want to return a representation
// to the client.

function handlePut() {
	// See comment in handlePost()

	var update = JSON.parse(String(document.container.entity.text));
	setState(update);
	
	return handleGet();
}

// This function is called for the DELETE verb, which is expected to behave as a
// logical "delete" of the resource's state.
//
// The expectation is that subsequent calls to handleGet() will fail. As such,
// it doesn't make sense to return a representation, and any returned value will
// ignored. Still, it's a good idea to return null to avoid any passing of value.

function handleDelete() {

	setState({});
	
	return null;
}
