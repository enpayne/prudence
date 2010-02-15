
# Helper to access the context attributes

def get_context_attribute name
	value = @prudence.resource.context.attributes[name]
	if value == nil
		value = yield

		# Note: another thread might have changed our value in the meantime.
		# We'll make sure there is no duplication.

		existing = @prudence.resource.context.attributes.put_if_absent name, value
		if existing != nil
			value = existing
		end

	end

	return value
end
