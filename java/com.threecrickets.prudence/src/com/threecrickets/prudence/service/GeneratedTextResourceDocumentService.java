/**
 * Copyright 2009-2010 Three Crickets LLC.
 * <p>
 * The contents of this file are subject to the terms of the LGPL version 3.0:
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * <p>
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly from Three Crickets
 * at http://threecrickets.com/
 */

package com.threecrickets.prudence.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.bcel.verifier.statics.StringRepresentation;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Reference;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.routing.Template;
import org.restlet.routing.Variable;

import com.threecrickets.prudence.DelegatedCacheKeyPatternHandler;
import com.threecrickets.prudence.GeneratedTextResource;
import com.threecrickets.prudence.cache.Cache;
import com.threecrickets.prudence.cache.CacheEntry;
import com.threecrickets.prudence.internal.GeneratedTextDeferredRepresentation;
import com.threecrickets.prudence.util.CaptiveRedirector;
import com.threecrickets.scripturian.Executable;
import com.threecrickets.scripturian.ExecutionContext;
import com.threecrickets.scripturian.LanguageManager;
import com.threecrickets.scripturian.document.DocumentDescriptor;
import com.threecrickets.scripturian.document.DocumentSource;
import com.threecrickets.scripturian.exception.DocumentException;
import com.threecrickets.scripturian.exception.DocumentNotFoundException;
import com.threecrickets.scripturian.exception.ExecutionException;
import com.threecrickets.scripturian.exception.ParsingException;

/**
 * Document service exposed to executables.
 * 
 * @author Tal Liron
 */
public class GeneratedTextResourceDocumentService extends ResourceDocumentServiceBase<GeneratedTextResource>
{
	//
	// Construction
	//

	/**
	 * Constructor.
	 * 
	 * @param resource
	 *        The resource
	 * @param executionContext
	 *        The execution context
	 * @param conversationService
	 *        The exposed conversation
	 */
	public GeneratedTextResourceDocumentService( GeneratedTextResource resource, ExecutionContext executionContext, Representation entity, Variant variant )
	{
		super( resource, resource.getDocumentSource() );
		this.executionContext = executionContext;
		conversationService = new GeneratedTextResourceConversationService( resource, entity, variant, resource.getDefaultCharacterSet() );
	}

	/**
	 * Construction by cloning, with new execution context (for deferred
	 * execution).
	 * 
	 * @param documentService
	 *        The document service to clone
	 */
	public GeneratedTextResourceDocumentService( GeneratedTextResourceDocumentService documentService )
	{
		super( documentService.resource, documentService.resource.getDocumentSource() );
		conversationService = documentService.conversationService;
		pushDocumentDescriptor( documentService.getCurrentDocumentDescriptor() );
		executionContext = new ExecutionContext();

		// Initialize execution context
		executionContext.getServices().put( resource.getDocumentServiceName(), this );
		executionContext.getServices().put( resource.getApplicationServiceName(), applicationService );
		executionContext.getServices().put( resource.getConversationServiceName(), conversationService );
		File libraryDirectory = resource.getLibraryDirectory();
		if( libraryDirectory != null )
			executionContext.getLibraryLocations().add( libraryDirectory.toURI() );

		conversationService.isDeferred = true;
	}

	//
	// Attributes
	//

	/**
	 * The cache duration.
	 * 
	 * @return The cache duration in milliseconds
	 * @see #setCacheDuration(long)
	 */
	public long getCacheDuration()
	{
		Long cacheDuration = (Long) getCurrentDocumentDescriptor().getDocument().getAttributes().get( CACHE_DURATION_ATTRIBUTE );
		return cacheDuration == null ? 0 : cacheDuration;
	}

	/**
	 * @param cacheDuration
	 *        The cache duration in milliseconds
	 * @see #getCacheDuration()
	 */
	public void setCacheDuration( long cacheDuration )
	{
		getCurrentDocumentDescriptor().getDocument().getAttributes().put( CACHE_DURATION_ATTRIBUTE, cacheDuration );
	}

	/**
	 * The cache key pattern.
	 * 
	 * @return The cache key pattern
	 * @see #setCacheKeyPattern(String)
	 */
	public String getCacheKeyPattern()
	{
		return getCacheKeyPattern( getCurrentDocumentDescriptor().getDocument() );
	}

	/**
	 * @param cacheKeyPattern
	 *        The cache key pattern
	 * @see #getCacheKeyPattern()
	 */
	public void setCacheKeyPattern( String cacheKeyPattern )
	{
		getCurrentDocumentDescriptor().getDocument().getAttributes().put( CACHE_KEY_PATTERN_ATTRIBUTE, cacheKeyPattern );
	}

	/**
	 * The cache key pattern handlers.
	 * 
	 * @return The cache key pattern handlers
	 */
	public ConcurrentMap<String, String> getCacheKeyPatternHandlers()
	{
		return getCacheKeyPatternHandlers( getCurrentDocumentDescriptor().getDocument(), true );
	}

	/**
	 * Casts the cache key pattern for the current executable.
	 * 
	 * @return The cache key for the current executable or null
	 */
	public String getCacheKey()
	{
		return castCacheKey( getCurrentDocumentDescriptor() );
	}

	/**
	 * @return The cache tags
	 */
	public Set<String> getCacheTags()
	{
		return getCacheTags( getCurrentDocumentDescriptor().getDocument(), true );
	}

	/**
	 * The cache.
	 * 
	 * @return The cache
	 */
	public Cache getCache()
	{
		return resource.getCache();
	}

	//
	// Operations
	//

	/**
	 * Includes a text document into the current location. The document may be a
	 * "text-with-scriptlets" executable, in which case its output could be
	 * dynamically generated.
	 * 
	 * @param documentName
	 *        The document name
	 * @return A representation of the document's output
	 * @throws ParsingException
	 * @throws ExecutionException
	 * @throws DocumentException
	 * @throws IOException
	 */
	public Representation include( String documentName ) throws ParsingException, ExecutionException, DocumentException, IOException
	{
		return include( documentName, true );
	}

	/**
	 * Includes a text document into the current location. The document may be a
	 * "text-with-scriptlets" executable, in which case its output could be
	 * dynamically generated.
	 * 
	 * @param documentName
	 *        The document name
	 * @param allowFragments
	 *        Whether to allow documents in the fragments directory
	 * @return A representation of the document's output
	 * @throws ParsingException
	 * @throws ExecutionException
	 * @throws DocumentException
	 * @throws IOException
	 * @see GeneratedTextResource#getFragmentDirectory()
	 */
	@SuppressWarnings("unchecked")
	public Representation include( String documentName, boolean allowFragments ) throws ParsingException, ExecutionException, DocumentException, IOException
	{
		documentName = resource.validateDocumentName( documentName );

		DocumentDescriptor<Executable> documentDescriptor;

		// See if a document descriptor is cached in the request
		documentDescriptor = (DocumentDescriptor<Executable>) resource.getRequest().getAttributes().remove( "com.threecrickets.prudence.GeneratedTextResource.documentDescriptor" );

		if( documentDescriptor == null )
		{
			try
			{
				documentDescriptor = Executable.createOnce( documentName, getSource(), true, resource.getLanguageManager(), resource.getDefaultLanguageTag(), resource.isPrepare() );
			}
			catch( DocumentNotFoundException x )
			{
				if( allowFragments )
				{
					// Try the fragment directory
					File fragmentDirectory = resource.getFragmentDirectoryRelative();
					if( fragmentDirectory != null )
						documentDescriptor = Executable.createOnce( fragmentDirectory.getPath() + "/" + documentName, getSource(), true, resource.getLanguageManager(), resource.getDefaultLanguageTag(),
							resource.isPrepare() );
					else
						throw x;
				}
				else
					throw x;
			}
		}

		// Add dependency
		DocumentDescriptor<Executable> currentDocumentDescriptor = getCurrentDocumentDescriptor();
		if( currentDocumentDescriptor != null )
			currentDocumentDescriptor.getDependencies().add( documentDescriptor );

		if( conversationService.getMediaType() == null )
			// Set initial media type according to the document's tag
			conversationService.setMediaTypeExtension( documentDescriptor.getTag() );

		// Execute
		pushDocumentDescriptor( documentDescriptor );
		try
		{
			return generateText( documentDescriptor.getDocument() );
		}
		finally
		{
			popDocumentDescriptor();
		}
	}

	/**
	 * Gets the cache entry for a document, if it exists and is valid.
	 * 
	 * @param documentName
	 *        The document name
	 * @return The cache entry
	 * @throws ParsingException
	 * @throws DocumentException
	 */
	public CacheEntry getCacheEntry( String documentName ) throws ParsingException, DocumentException
	{
		documentName = resource.validateDocumentName( documentName );

		DocumentDescriptor<Executable> documentDescriptor;
		documentDescriptor = Executable.createOnce( documentName, getSource(), true, resource.getLanguageManager(), resource.getDefaultLanguageTag(), resource.isPrepare() );

		// Cache the document descriptor in the request
		resource.getRequest().getAttributes().put( "com.threecrickets.prudence.GeneratedTextResource.documentDescriptor", documentDescriptor );

		String cacheKey = castCacheKey( documentDescriptor );
		if( cacheKey != null )
		{
			Cache cache = resource.getCache();
			if( cache != null )
			{
				CacheEntry cacheEntry = cache.fetch( cacheKey );
				if( cacheEntry != null )
				{
					// Make sure the document is not newer than the cache
					// entry
					if( documentDescriptor.getDocument().getDocumentTimestamp() <= cacheEntry.getDocumentModificationDate().getTime() )
					{
						// Cache the cache entry in the request
						resource.getRequest().getAttributes().put( "com.threecrickets.prudence.GeneratedTextResource.cacheEntry", cacheEntry );

						return cacheEntry;
					}
				}
			}
		}

		return null;
	}

	// //////////////////////////////////////////////////////////////////////////
	// Protected

	@Override
	protected DocumentDescriptor<Executable> getDocumentDescriptor( String documentName ) throws ParsingException, DocumentException
	{
		DocumentDescriptor<Executable> documentDescriptor;
		try
		{
			documentDescriptor = Executable.createOnce( documentName, getSource(), false, resource.getLanguageManager(), resource.getDefaultLanguageTag(), resource.isPrepare() );
		}
		catch( DocumentNotFoundException x )
		{
			File libraryDirectory = resource.getLibraryDirectoryRelative();
			if( libraryDirectory != null )
				// Try the library directory
				documentDescriptor = Executable.createOnce( libraryDirectory.getPath() + "/" + documentName, getSource(), false, resource.getLanguageManager(), resource.getDefaultLanguageTag(), resource.isPrepare() );
			else
				throw x;
		}

		return documentDescriptor;
	}

	// //////////////////////////////////////////////////////////////////////////
	// Private

	private static final String DOCUMENT_NAME_VARIABLE = "dn";

	private static final String DOCUMENT_NAME_VARIABLE_FULL = "{" + DOCUMENT_NAME_VARIABLE + "}";

	private static final String CLIENT_INFO_PREFERRED_ENCODING = "cipe";

	private static final String CLIENT_INFO_PREFERRED_ENCODING_FULL = "{" + CLIENT_INFO_PREFERRED_ENCODING + "}";

	private static final String APPLICATION_NAME_VARIABLE = "an";

	private static final String APPLICATION_NAME_VARIABLE_FULL = "{" + APPLICATION_NAME_VARIABLE + "}";

	private static final String PATH_TO_BASE_VARIABLE = "ptb";

	private static final String PATH_TO_BASE_VARIABLE_FULL = "{" + PATH_TO_BASE_VARIABLE + "}";

	private static final String CACHE_DURATION_ATTRIBUTE = "com.threecrickets.prudence.GeneratedTextResource.cacheDuration";

	private static final String CACHE_KEY_PATTERN_ATTRIBUTE = "com.threecrickets.prudence.GeneratedTextResource.cacheKeyPattern";

	private static final String CACHE_KEY_PATTERN_HANDLERS_ATTRIBUTE = "com.threecrickets.prudence.GeneratedTextResource.cacheKeyPatternHandlers";

	private static final String CACHE_TAGS_ATTRIBUTE = "com.threecrickets.prudence.GeneratedTextResource.cacheTags";

	/**
	 * The conversation service.
	 */
	private final GeneratedTextResourceConversationService conversationService;

	/**
	 * The application service.
	 */
	private final ApplicationService applicationService = new ApplicationService();

	/**
	 * The execution context.
	 */
	private final ExecutionContext executionContext;

	/**
	 * Buffer used for caching.
	 */
	private StringBuffer writerBuffer;

	/**
	 * The cache key pattern.
	 * 
	 * @param executable
	 *        The executable
	 * @return The cache key pattern
	 */
	private static String getCacheKeyPattern( Executable executable )
	{
		return (String) executable.getAttributes().get( CACHE_KEY_PATTERN_ATTRIBUTE );
	}

	/**
	 * @param executable
	 *        The executable
	 * @param create
	 *        Whether to create a handler map if it doesn't exist
	 * @return The handler map or null
	 */
	@SuppressWarnings("unchecked")
	private static ConcurrentMap<String, String> getCacheKeyPatternHandlers( Executable executable, boolean create )
	{
		ConcurrentMap<String, String> handlers = (ConcurrentMap<String, String>) executable.getAttributes().get( CACHE_KEY_PATTERN_HANDLERS_ATTRIBUTE );
		if( handlers == null && create )
		{
			handlers = new ConcurrentHashMap<String, String>();
			ConcurrentMap<String, String> existing = (ConcurrentMap<String, String>) executable.getAttributes().putIfAbsent( CACHE_KEY_PATTERN_HANDLERS_ATTRIBUTE, handlers );
			if( existing != null )
				handlers = existing;
		}

		return handlers;
	}

	/**
	 * @param executable
	 *        The executable
	 * @param create
	 *        Whether to create a cache tag set if it doesn't exist
	 * @return The cache tags or null
	 */
	@SuppressWarnings("unchecked")
	private static Set<String> getCacheTags( Executable executable, boolean create )
	{
		Set<String> cacheTags = (Set<String>) executable.getAttributes().get( CACHE_TAGS_ATTRIBUTE );
		if( cacheTags == null && create )
		{
			cacheTags = new CopyOnWriteArraySet<String>();
			Set<String> existing = (Set<String>) executable.getAttributes().putIfAbsent( CACHE_TAGS_ATTRIBUTE, cacheTags );
			if( existing != null )
				cacheTags = existing;
		}

		return cacheTags;
	}

	/**
	 * @return The cache expiration timestamp for the executable
	 */
	private static long getExpirationTimestamp( Executable executable )
	{
		Long cacheDurationLong = (Long) executable.getAttributes().get( CACHE_DURATION_ATTRIBUTE );
		long cacheDuration = cacheDurationLong == null ? 0 : cacheDurationLong;
		if( cacheDuration <= 0 )
			return 0;
		else
			return executable.getLastExecutedTimestamp() + cacheDuration;
	}

	/**
	 * Casts the cache key pattern for an executable.
	 * 
	 * @param documentDescriptor
	 *        The document descriptor
	 * @return The cache key or null
	 */
	private String castCacheKey( DocumentDescriptor<Executable> documentDescriptor )
	{
		String cacheKeyPattern = getCacheKeyPattern( documentDescriptor.getDocument() );
		if( cacheKeyPattern == null )
			return null;
		else
		{
			Template template = new Template( cacheKeyPattern );

			Reference captiveReference = CaptiveRedirector.getCaptiveReference( resource.getRequest() );
			Reference resourceReference = resource.getRequest().getResourceRef();

			// {dn}
			if( cacheKeyPattern.contains( DOCUMENT_NAME_VARIABLE_FULL ) )
				template.getVariables().put( DOCUMENT_NAME_VARIABLE, new Variable( Variable.TYPE_ALL, documentDescriptor.getDefaultName(), true, true ) );

			// {an}
			if( cacheKeyPattern.contains( APPLICATION_NAME_VARIABLE_FULL ) )
				template.getVariables().put( APPLICATION_NAME_VARIABLE, new Variable( Variable.TYPE_ALL, resource.getApplication().getName(), true, true ) );

			// {cipe}
			if( cacheKeyPattern.contains( CLIENT_INFO_PREFERRED_ENCODING_FULL ) )
				template.getVariables().put( CLIENT_INFO_PREFERRED_ENCODING, new Variable( Variable.TYPE_ALL, conversationService.getPreferredEncodingName(), true, true ) );

			// {ptb}
			if( cacheKeyPattern.contains( PATH_TO_BASE_VARIABLE_FULL ) )
				template.getVariables().put( PATH_TO_BASE_VARIABLE, new Variable( Variable.TYPE_ALL, conversationService.getPathToBase(), true, true ) );

			// Custom handlers
			Map<String, String> cacheKeyPatternHandlers = getCacheKeyPatternHandlers( documentDescriptor.getDocument(), false );
			if( cacheKeyPatternHandlers != null )
			{
				// Group variables together for handlers
				Map<String, Set<String>> delegatedHandlers = new HashMap<String, Set<String>>();
				for( Map.Entry<String, String> entry : cacheKeyPatternHandlers.entrySet() )
				{
					String variable = entry.getKey();
					String documentName = entry.getValue();
					if( cacheKeyPattern.contains( "{" + variable + "}" ) )
					{
						Set<String> variables = delegatedHandlers.get( documentName );
						if( variables == null )
						{
							variables = new HashSet<String>();
							delegatedHandlers.put( documentName, variables );
						}
						variables.add( variable );
					}
				}

				// Call handlers
				if( !delegatedHandlers.isEmpty() )
				{
					DocumentSource<Executable> handlersDocumentSource = resource.getHandlersDocumentSource();
					if( handlersDocumentSource == null )
						throw new RuntimeException( "Cannot use cacheKeyPatternHandlers if no handlersDocumentSource was set" );

					LanguageManager languageManager = resource.getLanguageManager();

					for( Map.Entry<String, Set<String>> entry : delegatedHandlers.entrySet() )
					{
						DelegatedCacheKeyPatternHandler delegatedHandler = new DelegatedCacheKeyPatternHandler( entry.getKey(), handlersDocumentSource, languageManager );
						delegatedHandler.handleCacheKeyPattern( entry.getValue().toArray( new String[] {} ) );
					}
				}
			}

			Request request = resource.getRequest();
			Response response = resource.getResponse();

			// Use captive reference as the resource reference
			if( captiveReference != null )
				request.setResourceRef( captiveReference );
			try
			{
				// Cast it
				return template.format( request, response );
			}
			finally
			{
				// Return regular reference
				if( captiveReference != null )
					request.setResourceRef( resourceReference );
			}
		}
	}

	/**
	 * Copies the cache tags for the current executable, if it has any, to the
	 * entire executable stack.
	 * 
	 * @param cacheTags
	 *        The cache tags
	 * @return The cleaned cache tags
	 */
	private Set<String> propagateCacheTags( Set<String> cacheTags )
	{
		ArrayList<String> propagatedCacheTags = new ArrayList<String>( cacheTags.size() );
		Set<String> cleanedCacheTags = new HashSet<String>( cacheTags.size() );

		for( String cacheTag : cacheTags )
		{
			// Don't propagate underscored cache tags
			if( cacheTag.startsWith( "_" ) )
				// But remove the underscore...
				cleanedCacheTags.add( cacheTag.substring( 1 ) );
			else
			{
				propagatedCacheTags.add( cacheTag );
				cleanedCacheTags.add( cacheTag );
			}
		}

		if( !propagatedCacheTags.isEmpty() )
		{
			DocumentDescriptor<Executable> currentDocumentDescriptor = popDocumentDescriptor();
			for( DocumentDescriptor<Executable> documentDescriptor : documentDescriptorStack )
				getCacheTags( documentDescriptor.getDocument(), true ).addAll( propagatedCacheTags );
			pushDocumentDescriptor( currentDocumentDescriptor );
		}

		return cleanedCacheTags;
	}

	/*
	 * private Object x( Executable executable ) { // Filter before handling
	 * List<DelegatedFilter> filters = getFilters( executable, false );
	 * ListIterator<DelegatedFilter> documentFilterIterator = null; boolean skip
	 * = false; if( filters != null ) { documentFilterIterator =
	 * filters.listIterator(); while( documentFilterIterator.hasNext() ) {
	 * DelegatedFilter delegatedFilter = documentFilterIterator.next(); int
	 * action = delegatedFilter.handleBefore( resource.getRequest(),
	 * resource.getResponse() ); switch( action ) { case Filter.STOP: return
	 * null; case Filter.SKIP: skip = true; break; } } } // Filter after
	 * handling if( documentFilterIterator != null ) { while(
	 * documentFilterIterator.hasPrevious() ) { DelegatedFilter delegatedFilter
	 * = documentFilterIterator.previous(); delegatedFilter.handleAfter(
	 * resource.getRequest(), resource.getResponse() ); } } if( filters != null
	 * ) for( ListIterator<DelegatedFilter> i = filters.listIterator(
	 * filters.size() ); i.hasPrevious(); ) i.previous().handleAfter(
	 * resource.getRequest(), resource.getResponse() ); return null; }
	 */

	/**
	 * Generates and possibly caches a textual representation. The returned
	 * representation is either a {@link StringRepresentation} or a
	 * {@link GeneratedTextDeferredRepresentation}. Text in the former case
	 * could be the result of either execution or retrieval from the cache.
	 * 
	 * @param executable
	 *        The executable
	 * @return A representation, either generated by the executable or fetched
	 *         from the cache
	 * @throws IOException
	 * @throws ParsingException
	 * @throws ExecutionException
	 */
	private Representation generateText( Executable executable ) throws IOException, ParsingException, ExecutionException
	{
		Writer writer = executionContext.getWriter();

		// Optimized handling for pure text
		String pureText = executable.getAsPureLiteral();
		if( pureText != null )
		{
			// We want to write this, too, for includes
			if( writer != null )
				writer.write( pureText );

			return new CacheEntry( pureText, conversationService.getMediaType(), conversationService.getLanguage(), conversationService.getCharacterSet(), conversationService.getEncoding(),
				executable.getDocumentTimestamp(), getExpirationTimestamp( executable ) ).represent();
		}

		int startPosition = 0;

		// Make sure we have a valid writer for caching mode
		if( !conversationService.isDeferred )
		{
			if( writer == null )
			{
				StringWriter stringWriter = new StringWriter();
				writerBuffer = stringWriter.getBuffer();
				writer = new BufferedWriter( stringWriter );
				executionContext.setWriter( writer );
			}
			else
			{
				writer.flush();
				startPosition = writerBuffer.length();
			}

			// See if a valid cache entry has already been cached in the request
			CacheEntry cacheEntry = (CacheEntry) resource.getRequest().getAttributes().get( "com.threecrickets.prudence.GeneratedTextResource.cacheEntry" );
			if( cacheEntry != null )
			{
				// We want to write this, too, for includes
				if( ( writer != null ) && ( cacheEntry.getString() != null ) )
					writer.write( cacheEntry.getString() );

				return cacheEntry.represent();
			}

			// Attempt to use cache
			String cacheKey = getCacheKey();
			if( cacheKey != null )
			{
				Cache cache = resource.getCache();
				if( cache != null )
				{
					cacheEntry = cache.fetch( cacheKey );
					if( cacheEntry != null )
					{
						// Make sure the document is not newer than the cache
						// entry
						if( executable.getDocumentTimestamp() <= cacheEntry.getDocumentModificationDate().getTime() )
						{
							// We want to write this, too, for includes
							if( ( writer != null ) && ( cacheEntry.getString() != null ) )
								writer.write( cacheEntry.getString() );

							return cacheEntry.represent();
						}
					}
				}
			}
		}

		setCacheDuration( 0 );
		setCacheKeyPattern( resource.getDefaultCacheKeyPattern() );
		getCacheTags().clear();

		try
		{
			executionContext.setWriter( writer );
			executionContext.getServices().put( resource.getDocumentServiceName(), this );
			executionContext.getServices().put( resource.getApplicationServiceName(), applicationService );
			executionContext.getServices().put( resource.getConversationServiceName(), conversationService );

			// Execute!
			executable.execute( executionContext, this, resource.getExecutionController() );

			// Did the executable ask us to defer?
			if( conversationService.defer )
			{
				conversationService.defer = false;

				// Note that this will cause the executable to execute
				// again!
				GeneratedTextResourceDocumentService documentService = new GeneratedTextResourceDocumentService( this );
				return new GeneratedTextDeferredRepresentation( documentService.resource, executable, documentService.executionContext, documentService, documentService.conversationService );
			}

			if( conversationService.isDeferred )
			{
				// Nothing to return in deferred mode
				return null;
			}
			else
			{
				writer.flush();

				// Get the buffer from when we executed the executable
				CacheEntry cacheEntry = new CacheEntry( writerBuffer.substring( startPosition ), conversationService.getMediaType(), conversationService.getLanguage(), conversationService.getCharacterSet(),
					conversationService.getEncoding(), executable.getDocumentTimestamp(), getExpirationTimestamp( executable ) );

				// Cache if enabled
				String cacheKey = getCacheKey();
				if( ( cacheKey != null ) && ( cacheEntry.getExpirationDate() != null ) )
				{
					Set<String> cacheTags = getCacheTags( executable, false );

					// Propagate cache tags up the stack
					if( ( cacheTags != null ) && !cacheTags.isEmpty() )
						cacheTags = propagateCacheTags( cacheTags );

					Cache cache = resource.getCache();
					if( cache != null )
					{
						// Cache!
						cache.store( cacheKey, cacheTags, cacheEntry );
					}
				}

				// Return a representation of the entire buffer
				if( startPosition == 0 )
					return cacheEntry.represent();
				else
					return new CacheEntry( writerBuffer.toString(), conversationService.getMediaType(), conversationService.getLanguage(), conversationService.getCharacterSet(), conversationService.getEncoding(),
						executable.getDocumentTimestamp(), getExpirationTimestamp( executable ) ).represent();
			}
		}
		catch( ExecutionException x )
		{
			// Did the executable ask us to defer?
			if( conversationService.defer )
			{
				// Note that we will allow exceptions in an executable that
				// ask us to defer! In fact, throwing an exception is a good
				// way for the executable to signal that it's done and is
				// ready to defer.

				conversationService.defer = false;

				// Note that this will cause the executable to run again!
				GeneratedTextResourceDocumentService documentService = new GeneratedTextResourceDocumentService( this );
				return new GeneratedTextDeferredRepresentation( documentService.resource, executable, documentService.executionContext, documentService, documentService.conversationService );
			}
			else
				throw x;
		}
		finally
		{
			writer.flush();
			executionContext.getErrorWriterOrDefault().flush();
		}
	}
}