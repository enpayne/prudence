# Helper to access the context attributes

def getContextAttribute(name, getDefaultValue):
	value = document.container.resource.context.attributes[name]
	if value == None:
		value = getDefaultValue()

		# Note: another thread might have changed our value in the meantime.
		# We'll make sure there is no duplication.

		existing = document.container.resource.context.attributes.putIfAbsent(name, value)
		if existing != None:
			value = existing

	return value