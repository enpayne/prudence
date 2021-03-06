
document.execute('stickstick/data/')

import java.lang.System
import org.json.JSONObject

merge = { key, a, b ->
	if(!a.containsKey(key)) {
		return b[key]
	}
	else {
		return a[key]
	}
}

getId = { conversation ->
    try {
        return Integer.parseInt(conversation.locals.id)
    }
    catch(Exception) {
    	return null
    }

	// return Integer.parseInt(conversation.query.id)
}

handleInit = { conversation ->
    conversation.addMediaTypeByName('text/plain')
    conversation.addMediaTypeByName('application/json')
}

handleGet = { conversation ->
	def id = getId(conversation)
	
    def note
    def connection = getConnection()
    try {
        note = getNote(id, connection)
        if(note == null) {
        	return 404
        }
    }
    finally {
    	connection.close()
    }

    conversation.modificationTimestamp = note.timestamp
    note.remove('timestamp')
    return new JSONObject(note)
}

handleGetInfo = { conversation ->
	def id = getId(conversation)
	
    def note
    def connection = getConnection()
    try {
        note = getNote(id, connection)
        if(note == null) {
        	return null
        }
    }
    finally {
    	connection.close()
    }

    return note.timestamp
}

handlePost = { conversation ->
	def id = getId(conversation)

    // Note: You can only "consume" the entity once, so if we want it
    // as text, and want to refer to it more than once, we should keep
    // a reference to that text.
    
    def text = conversation.entity.text
    def entity = new JSONObject(text)
    def note = [:]
	for(def key in entity.keys()) {
		note[key] = entity.get(key)
	}

    def connection = getConnection()
    try {
        def existing = getNote(id, connection)
        if(existing == null) {
        	return 404
        }
        note = [
        	id: id,
        	board: merge('board', note, existing),
        	x: merge('x', note, existing),
        	y: merge('y', note, existing),
        	size: merge('size', note, existing),
        	content: merge('content', note, existing)
        ]
        updateNote(note, connection)
        updateBoardTimestamp(note, connection)
    }
    finally {
    	connection.close()
    }

    conversation.modificationTimestamp = note.timestamp
    note.remove('timestamp')
    return new JSONObject(note)
}

handleDelete = { conversation ->
	def id = getId(conversation)

    def connection = getConnection()
    try { 
        def note = getNote(id, connection)
        if(note == null) {
        	return 404
        }
        deleteNote(note, connection)
        updateBoardTimestamp(note, connection, System.currentTimeMillis())
    }
    finally {
    	connection.close()
    }

    return null
}
