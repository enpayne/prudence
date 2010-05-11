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

package com.threecrickets.prudence;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.data.CacheDirective;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.data.Language;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.representation.Variant;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import com.threecrickets.prudence.cache.Cache;
import com.threecrickets.prudence.internal.ExposedDocumentForGeneratedTextResource;
import com.threecrickets.prudence.internal.JygmentsDocumentFormatter;
import com.threecrickets.scripturian.Executable;
import com.threecrickets.scripturian.ExecutionContext;
import com.threecrickets.scripturian.ExecutionController;
import com.threecrickets.scripturian.LanguageManager;
import com.threecrickets.scripturian.document.DocumentDescriptor;
import com.threecrickets.scripturian.document.DocumentFileSource;
import com.threecrickets.scripturian.document.DocumentFormatter;
import com.threecrickets.scripturian.document.DocumentSource;
import com.threecrickets.scripturian.exception.DocumentException;
import com.threecrickets.scripturian.exception.DocumentNotFoundException;
import com.threecrickets.scripturian.exception.ExecutionException;
import com.threecrickets.scripturian.exception.ParsingException;
import com.threecrickets.scripturian.internal.ScripturianUtil;

/**
 * A Restlet resource which executes a "text with scriptlets" Scripturian
 * {@link Executable} document for HTTP GET and POST verbs and redirects its
 * standard output to a {@link StringRepresentation}.
 * <p>
 * Before using this resource, make sure to configure a valid document source in
 * the application's {@link Context}; see {@link #getDocumentSource()}. This
 * document source is exposed to scriptlets as <code>prudence.source</code>.
 * <p>
 * This resource supports two modes of output:
 * <ul>
 * <li>Caching mode: First, the entire document is executed, with its output
 * sent into a buffer. This buffer is then cached, and <i>only then</i> sent to
 * the client. This is the default mode and recommended for most documents.
 * Scriptlets can control the duration of their individual cache by changing the
 * value of <code>executable.cacheDuration</code> (see {@link Executable}).
 * Because output is not sent to the client until after the executable finished
 * its execution, it is possible for scriptlets to set output characteristics at
 * any time by changing the values of <code>prudence.mediaType</code>,
 * <code>prudence.characterSet</code>, and <code>prudence.language</code> (see
 * below).</li>
 * <li>Streaming mode: Output is sent to the client <i>while</i> the document is
 * being executed. This is recommended for documents that need to output a very
 * large amount of text, which might take a long time, or that might otherwise
 * encounter slow-downs while running. In either case, you want the client to
 * receive ongoing output. The output of the document is not cached, and the
 * value of <code>executable.cacheDuration</code> is always reset to 0 in this
 * mode. To enter streaming mode, call <code>prudence.stream()</code> (see below
 * for details). Note that you must determine output characteristics (
 * <code>prudence.mediaType</code>, <code>prudence.characterSet</code>, and
 * <code>prudence.language</code>) <i>before</i> entering streaming mode. Trying
 * to change them while running in streaming mode will raise an exception.
 * </ul>
 * <p>
 * A special container environment is created for your scriptlets, with some
 * useful services. It is exposed to scriptlets as a global variables named
 * <code>prudence</code>. For some other global variables exposed to scriptlets,
 * see {@link Executable}.
 * <p>
 * Operations:
 * <ul>
 * <li><code>prudence.includeDocument(name)</code>: This powerful method allows
 * scriptlets to execute other documents in place, and is useful for creating
 * large, maintainable applications based on documents. Included documents can
 * act as a library or toolkit and can even be shared among many applications.
 * The included document does not have to be in the same programming language or
 * use the same engine as the calling scriptlet. However, if they do use the
 * same engine, then methods, functions, modules, etc., could be shared.
 * <p>
 * It is important to note that how this works varies a lot per engine. For
 * example, in JRuby, every scriptlet is run in its own scope, so that sharing
 * would have to be done explicitly in the global scope. See the included JRuby
 * examples for a discussion of various ways to do this.
 * </li>
 * <li><code>prudence.include(name)</code>:except that the document is parsed as
 * a single, non-delimited script with the engine name derived from name's
 * extension.</li>
 * <li><code>conversation.stream()</code>: If you are in caching mode, calling
 * this method will return true and cause the document to run again, where this
 * next run will be in streaming mode. Whatever output the document created in
 * the current run is discarded, and all further exceptions are ignored. For
 * this reason, it's probably best to call <code>conversation.stream()</code> as
 * early as possible in the document, and then to quit the document as soon as
 * possible if it returns true. For example, your document can start by testing
 * whether it will have a lot of output, and if so, set output characteristics,
 * call <code>conversation.stream()</code>, and quit. If you are already in
 * streaming mode, calling this method has no effect and returns false. Note
 * that a good way to quit the script is to throw an exception, because it will
 * end the script and otherwise be ignored. By default, writers will be
 * automatically flushed after every line in streaming mode. If you want to
 * disable this behavior, use <code>conversation.stream(flushLines)</code> .</li>
 * <li><code>conversation.stream(flushLines)</code>: This version of the above
 * adds a boolean argument to let you control whether to flush the writer after
 * every line in streaming mode. By default line-by-line flushing is enabled.</li>
 * </ul>
 * Read-only attributes:
 * <ul>
 * <li><code>conversation.entity</code>: The entity of this request. Available
 * only for post and put.</li>
 * <li><code>conversation.isInternal</code>: This boolean is true if the request
 * was received via the RIAP protocol.</li>
 * <li><code>conversation.isStreaming</code>: This boolean is true when the
 * writer is in streaming mode (see above).</li>
 * <li><code>conversation.resource</code>: The instance of this resource. Acts
 * as a "this" reference for scriptlets. You can use it to access the request
 * and response.</li>
 * <li><code>prudence.source</code>: The source used for the script; see
 * {@link #getDocumentSource()}.</li>
 * <li><code>conversation.variant</code>: The {@link Variant} of this request.
 * Useful for interrogating the client's preferences.</li>
 * </ul>
 * Modifiable attributes:
 * <ul>
 * <li><code>prudence.cacheDuration</code>: Setting this to something greater
 * than 0 enables caching of the executable's output for a maximum number of
 * milliseconds. By default {@code cacheDuration} is 0.</li>
 * <li><code>prudence.cacheGroups</code>: An options list of groups for our
 * cache entry to be associated with. Cache groups make it easy to invalidate
 * many entries in the cache at once. (See {@link Cache#invalidate(String)})</li>
 * <li><code>prudence.cacheKey</code>: A template for defining how the cache key
 * will be generated.</li>
 * <li><code>conversation.characterSet</code>: The {@link CharacterSet} that
 * will be used for the generated string. Defaults to what the client requested
 * (in <code>prudence.variant</code>), or to the value of
 * {@link #getDefaultCharacterSet()} if the client did not specify it. If not in
 * streaming mode, your scriptlets can change this to something else.</li>
 * <li><code>prudence.language</code>: The {@link Language} that will be used
 * for the generated string. Defaults to null. If not in streaming mode, your
 * scriptlets can change this to something else.</li>
 * <li><code>conversation.mediaType</code>: The {@link MediaType} that will be
 * used for the generated string. Defaults to what the client requested (in
 * <code>prudence.variant</code>). If not in streaming mode, your scriptlets can
 * change this to something else.</li>
 * <li><code>conversation.statusCode</code>: A convenient way to set the
 * response status code. This is equivalent to setting
 * <code>prudence.resource.response.status</code> using
 * {@link Status#valueOf(int)}.</li>
 * </ul>
 * <p>
 * In addition to the above, a {@link ExecutionController} can be set to expose
 * your own global variables to scriptlets. See
 * {@link #getExecutionController()}.
 * <p>
 * Summary of settings configured via the application's {@link Context}:
 * <ul>
 * <li>
 * <code>com.threecrickets.prudence.cache:</code> {@link Cache}. See
 * {@link #getCache()}.</li>
 * <li>
 * <code>com.threecrickets.prudence.GeneratedTextResource.clientCachingMode:</code>
 * {@link Integer}, defaults to {@link #CLIENT_CACHING_MODE_CONDITIONAL}. See
 * {@link #getClientCachingMode()}.</li>
 * <li>
 * <code>com.threecrickets.prudence.GeneratedTextResource.prepare:</code>
 * {@link Boolean}, defaults to true. See {@link #isPrepare()}.</li>
 * <li>
 * <code>com.threecrickets.prudence.GeneratedTextResource.exposedContainerName</code>
 * : The name of the global variable with which to access the container.
 * Defaults to "prudence". See {@link #getExposedDocumentName()}.</li>
 * <li>
 * <code>com.threecrickets.prudence.GeneratedTextResource.defaultCacheKey:</code>
 * {@link String}, defaults to "{ri}". See {@link #getDefaultCacheKey()}.</li>
 * <li>
 * <code>com.threecrickets.prudence.GeneratedTextResource.defaultCharacterSet:</code>
 * {@link CharacterSet}, defaults to {@link CharacterSet#UTF_8}. See
 * {@link #getDefaultCharacterSet()}.</li>
 * <li>
 * <code>com.threecrickets.prudence.GeneratedTextResource.defaultLanguageTag:</code>
 * {@link String}, defaults to "js". See {@link #getDefaultLanguageTag()}.</li>
 * <li>
 * <code>com.threecrickets.prudence.GeneratedTextResource.defaultName:</code>
 * {@link String}, defaults to "index.page". See {@link #getDefaultName()}.</li>
 * <li>
 * <code>com.threecrickets.prudence.GeneratedTextResource.documentSource:</code>
 * {@link DocumentSource}. <b>Required.</b> See {@link #getDocumentSource()}.</li>
 * <li>
 * <code>com.threecrickets.prudence.GeneratedTextResource.languageManager:</code>
 * {@link LanguageManager}, defaults to a new instance. See
 * {@link #getLanguageManager()}.</li>
 * <li>
 * <code>com.threecrickets.prudence.GeneratedTextResource.sourceViewable:</code>
 * {@link Boolean}, defaults to false. See {@link #isSourceViewable()}.</li>
 * <li>
 * <code>com.threecrickets.prudence.GeneratedTextResource.executionController:</code>
 * {@link ExecutionController}. See {@link #getExecutionController()}.</li>
 * <li>
 * <code>com.threecrickets.prudence.GeneratedTextResource.trailingSlashRequired:</code>
 * {@link Boolean}, defaults to true. See {@link #isTrailingSlashRequired()}.</li>
 * </ul>
 * <p>
 * <i>"Restlet" is a registered trademark of <a
 * href="http://www.restlet.org/about/legal">Noelios Technologies</a>.</i>
 * 
 * @author Tal Liron
 * @see Executable
 * @see DelegatedResource
 */
public class GeneratedTextResource extends ServerResource
{
	//
	// Constants
	//

	public static final int CLIENT_CACHING_MODE_DISABLED = 0;

	public static final int CLIENT_CACHING_MODE_CONDITIONAL = 1;

	public static final int CLIENT_CACHING_MODE_OFFLINE = 2;

	//
	// Attributes
	//

	/**
	 * The name of the global variable with which to access the document.
	 * Defaults to "document".
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.exposedContainerName</code>
	 * in the application's {@link Context}.
	 * 
	 * @return The document name
	 */
	public String getExposedDocumentName()
	{
		if( exposedDocumentName == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			exposedDocumentName = (String) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.exposedDocumentName" );

			if( exposedDocumentName == null )
				exposedDocumentName = "document";
		}

		return exposedDocumentName;
	}

	/**
	 * The name of the global variable with which to access the application.
	 * Defaults to "application".
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.exposedApplicationName</code>
	 * in the application's {@link Context}.
	 * 
	 * @return The application name
	 */
	public String getExposedApplicationName()
	{
		if( exposedApplicationName == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			exposedApplicationName = (String) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.exposedApplicationName" );

			if( exposedApplicationName == null )
				exposedApplicationName = "application";
		}

		return exposedApplicationName;
	}

	/**
	 * The name of the global variable with which to access the conversation.
	 * Defaults to "conversation".
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.exposedConversationName</code>
	 * in the application's {@link Context}.
	 * 
	 * @return The conversation name
	 */
	public String getExposedConversationName()
	{
		if( exposedConversationName == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			exposedConversationName = (String) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.exposedConversationName" );

			if( exposedConversationName == null )
				exposedConversationName = "conversation";
		}

		return exposedConversationName;
	}

	/**
	 * Cache used for caching mode. It is stored in the application's
	 * {@link Context} for persistence across requests and for sharing among
	 * instances of {@link GeneratedTextResource}.
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.cache</code> in the application's
	 * {@link Context}.
	 * <p>
	 * Note that this instance is shared with {@link DelegatedResource}.
	 * 
	 * @return The cache or null
	 * @see DelegatedResource#getCache()
	 */
	public Cache getCache()
	{
		if( cache == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			cache = (Cache) attributes.get( "com.threecrickets.prudence.cache" );
		}

		return cache;
	}

	/**
	 * The default character set to be used if the client does not specify it.
	 * Defaults to {@link CharacterSet#UTF_8}.
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.defaultCharacterSet</code>
	 * in the application's {@link Context}.
	 * 
	 * @return The default character set
	 */
	public CharacterSet getDefaultCharacterSet()
	{
		if( defaultCharacterSet == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			defaultCharacterSet = (CharacterSet) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.defaultCharacterSet" );

			if( defaultCharacterSet == null )
				defaultCharacterSet = CharacterSet.UTF_8;
		}

		return defaultCharacterSet;
	}

	/**
	 * If a URL points to a directory rather than a file, and that directory
	 * contains a file with this name, then it will be used. This allows you to
	 * use the directory structure to create nice URLs that do not contain
	 * filenames. Defaults to "index".
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.defaultName</code>
	 * in the application's {@link Context}.
	 * 
	 * @return The default name
	 */
	public String getDefaultName()
	{
		if( defaultName == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			defaultName = (String) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.defaultName" );

			if( defaultName == null )
				defaultName = "index";
		}

		return defaultName;
	}

	/**
	 * The default language tag to use if the first scriptlet doesn't specify
	 * one. Defaults to "js".
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.defaultLanguageTag</code>
	 * in the application's {@link Context}.
	 * 
	 * @return The language tag
	 */
	public String getDefaultLanguageTag()
	{
		if( defaultLanguageTag == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			defaultLanguageTag = (String) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.defaultLanguageTag" );

			if( defaultLanguageTag == null )
				defaultLanguageTag = "js";
		}

		return defaultLanguageTag;
	}

	/**
	 * The default cache key to use if the executable doesn't specify one.
	 * Defaults to "{ri}|{dn}".
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.defaultCacheKey</code>
	 * in the application's {@link Context}.
	 * 
	 * @return The default cache key
	 */
	public String getDefaultCacheKey()
	{
		if( defaultCacheKey == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			defaultCacheKey = (String) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.defaultCacheKey" );

			if( defaultCacheKey == null )
				defaultCacheKey = "{ri}|{dn}";
		}

		return defaultCacheKey;
	}

	/**
	 * An optional {@link ExecutionController} to be used with the scriptlets.
	 * Useful for exposing your own global variables to the scriptlets.
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.executionController</code>
	 * in the application's {@link Context}.
	 * 
	 * @return The execution controller or null if none used
	 */
	public ExecutionController getExecutionController()
	{
		if( executionController == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			executionController = (ExecutionController) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.executionController" );
		}

		return executionController;
	}

	/**
	 * The {@link LanguageManager} used to create the language adapters. Uses a
	 * default instance, but can be set to something else.
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.languageManager</code>
	 * in the application's {@link Context}.
	 * 
	 * @return The language manager
	 */
	public LanguageManager getLanguageManager()
	{
		if( languageManager == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			languageManager = (LanguageManager) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.languageManager" );

			if( languageManager == null )
			{
				languageManager = new LanguageManager();

				LanguageManager existing = (LanguageManager) attributes.putIfAbsent( "com.threecrickets.prudence.GeneratedTextResource.languageManager", languageManager );
				if( existing != null )
					languageManager = existing;
			}
		}

		return languageManager;
	}

	/**
	 * The {@link DocumentSource} used to fetch documents. This must be set to a
	 * valid value before this class is used!
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.documentSource</code>
	 * in the application's {@link Context}.
	 * 
	 * @return The document source
	 */
	@SuppressWarnings("unchecked")
	public DocumentSource<Executable> getDocumentSource()
	{
		if( documentSource == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			documentSource = (DocumentSource<Executable>) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.documentSource" );

			if( documentSource == null )
				throw new RuntimeException( "Attribute com.threecrickets.prudence.GeneratedTextResource.documentSource must be set in context to use GeneratedTextResource" );
		}

		return documentSource;
	}

	/**
	 * Executables might use this directory for including fragments. If the
	 * {@link #getDocumentSource()} is a {@link DocumentFileSource}, then this
	 * will default to the {@link DocumentFileSource#getBasePath()} plus
	 * "../fragments/".
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.fragmentDirectory</code>
	 * in the application's {@link Context}.
	 * 
	 * @return The fragment directory or null
	 */
	public File getFragmentDirectory()
	{
		if( fragmentDirectory == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			fragmentDirectory = (File) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.fragmentDirectory" );

			if( fragmentDirectory == null )
			{
				DocumentSource<Executable> documentSource = getDocumentSource();
				if( documentSource instanceof DocumentFileSource<?> )
				{
					fragmentDirectory = new File( ( (DocumentFileSource<?>) documentSource ).getBasePath(), "../fragments/" );

					File existing = (File) attributes.putIfAbsent( "com.threecrickets.prudence.GeneratedTextResource.fragmentDirectory", fragmentDirectory );
					if( existing != null )
						fragmentDirectory = existing;
				}
			}
		}

		return fragmentDirectory;
	}

	/**
	 * If the {@link #getDocumentSource()} is a {@link DocumentFileSource}, then
	 * this is the fragment directory relative to the
	 * {@link DocumentFileSource#getBasePath()}. Otherwise, it's null.
	 * 
	 * @return The relative fragment directory or null
	 */
	public File getFragmentDirectoryRelative()
	{
		DocumentSource<Executable> documentSource = getDocumentSource();
		if( documentSource instanceof DocumentFileSource<?> )
		{
			File fragmentDirectory = getFragmentDirectory();
			if( fragmentDirectory != null )
				return ScripturianUtil.getRelativeFile( fragmentDirectory, ( (DocumentFileSource<?>) documentSource ).getBasePath() );
		}
		return null;
	}

	/**
	 * Executables might use this directory for importing libraries. If the
	 * {@link #getDocumentSource()} is a {@link DocumentFileSource}, then this
	 * will default to the {@link DocumentFileSource#getBasePath()} plus
	 * "../../libraries/".
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.libraryDirectory</code>
	 * in the application's {@link Context}.
	 * 
	 * @return The library directory or null
	 * @see ExecutionContext#getLibraryLocations()
	 */
	public File getLibraryDirectory()
	{
		if( libraryDirectory == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			libraryDirectory = (File) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.libraryDirectory" );

			if( libraryDirectory == null )
			{
				DocumentSource<Executable> documentSource = getDocumentSource();
				if( documentSource instanceof DocumentFileSource<?> )
				{
					libraryDirectory = new File( ( (DocumentFileSource<?>) documentSource ).getBasePath(), "../../libraries/" );

					File existing = (File) attributes.putIfAbsent( "com.threecrickets.prudence.GeneratedTextResource.libraryDirectory", libraryDirectory );
					if( existing != null )
						libraryDirectory = existing;
				}
			}
		}

		return libraryDirectory;
	}

	/**
	 * If the {@link #getDocumentSource()} is a {@link DocumentFileSource}, then
	 * this is the library directory relative to the
	 * {@link DocumentFileSource#getBasePath()}. Otherwise, it's null.
	 * 
	 * @return The relative library directory or null
	 */
	public File getLibraryDirectoryRelative()
	{
		DocumentSource<Executable> documentSource = getDocumentSource();
		if( documentSource instanceof DocumentFileSource<?> )
		{
			File libraryDirectory = getLibraryDirectory();
			if( libraryDirectory != null )
				return ScripturianUtil.getRelativeFile( libraryDirectory, ( (DocumentFileSource<?>) documentSource ).getBasePath() );
		}
		return null;
	}

	/**
	 * Whether or not trailing slashes are required. Defaults to true.
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.trailingSlashRequired</code>
	 * in the application's {@link Context}.
	 * 
	 * @return Whether to allow client caching
	 */
	public boolean isTrailingSlashRequired()
	{
		if( trailingSlashRequired == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			trailingSlashRequired = (Boolean) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.trailingSlashRequired" );

			if( trailingSlashRequired == null )
				trailingSlashRequired = true;
		}

		return trailingSlashRequired;
	}

	/**
	 * Whether or not to send information to the client about cache expiration.
	 * Defaults to {@link #CLIENT_CACHING_MODE_CONDITIONAL}.
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.clientCachingMode</code>
	 * in the application's {@link Context}.
	 * 
	 * @return The client caching mode
	 */
	public int getClientCachingMode()
	{
		if( clientCachingMode == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			clientCachingMode = (Integer) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.clientCachingMode" );

			if( clientCachingMode == null )
				clientCachingMode = CLIENT_CACHING_MODE_CONDITIONAL;
		}

		return clientCachingMode;
	}

	/**
	 * Whether to prepare the executables. Preparation increases initialization
	 * time and reduces execution time. Note that not all languages support
	 * preparation as a separate operation. Defaults to true.
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.prepare</code> in
	 * the application's {@link Context}.
	 * 
	 * @return Whether to prepare executables
	 */
	public boolean isPrepare()
	{
		if( prepare == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			prepare = (Boolean) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.prepare" );

			if( prepare == null )
				prepare = true;
		}

		return prepare;
	}

	/**
	 * This is so we can see the source code for documents by adding
	 * <code>?source=true</code> to the URL. You probably wouldn't want this for
	 * most applications. Defaults to false.
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedTextResource.sourceViewable</code>
	 * in the application's {@link Context}.
	 * 
	 * @return Whether to allow viewing of source code
	 */
	public boolean isSourceViewable()
	{
		if( sourceViewable == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			sourceViewable = (Boolean) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.sourceViewable" );

			if( sourceViewable == null )
				sourceViewable = false;
		}

		return sourceViewable;
	}

	/**
	 * An optional {@link DocumentFormatter} to use for representing source
	 * code. Defaults to a {@link JygmentsDocumentFormatter}.
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.GeneratedtextResource.documentFormatter</code>
	 * in the application's {@link Context}.
	 * 
	 * @return The document formatter or null
	 * @see #isSourceViewable()
	 */
	@SuppressWarnings("unchecked")
	public DocumentFormatter<Executable> getDocumentFormatter()
	{
		if( documentFormatter == null )
		{
			ConcurrentMap<String, Object> attributes = getContext().getAttributes();
			documentFormatter = (DocumentFormatter<Executable>) attributes.get( "com.threecrickets.prudence.GeneratedTextResource.documentFormatter" );

			if( documentFormatter == null )
			{
				documentFormatter = new JygmentsDocumentFormatter<Executable>();

				DocumentFormatter<Executable> existing = (DocumentFormatter<Executable>) attributes.putIfAbsent( "com.threecrickets.prudence.GeneratedTextResource.documentFormatter", documentFormatter );
				if( existing != null )
					documentFormatter = existing;
			}
		}

		return documentFormatter;
	}

	//
	// Operations
	//

	/**
	 * Throws an exception if the document name is not valid. Uses
	 * {@link #getDefaultName()} if no name is given, and respect
	 * {@link #isTrailingSlashRequired()}.
	 * 
	 * @param documentName
	 *        The document name
	 * @return The valid document name
	 * @throws ResourceException
	 */
	public String validateDocumentName( String documentName ) throws ResourceException
	{
		if( isTrailingSlashRequired() )
			if( ( documentName != null ) && ( documentName.length() != 0 ) && !documentName.endsWith( "/" ) )
				throw new ResourceException( Status.CLIENT_ERROR_NOT_FOUND );

		if( ( documentName == null ) || ( documentName.length() == 0 ) || ( documentName.equals( "/" ) ) )
		{
			documentName = getDefaultName();
			if( isTrailingSlashRequired() && !documentName.endsWith( "/" ) )
				documentName += "/";
		}

		return documentName;
	}

	//
	// ServerResource
	//

	/**
	 * Initializes the resource.
	 */
	@Override
	protected void doInit() throws ResourceException
	{
		super.doInit();
		setAnnotated( false );
	}

	@Override
	public Representation get() throws ResourceException
	{
		return execute( null, null );
	}

	@Override
	public Representation get( Variant variant ) throws ResourceException
	{
		return execute( null, variant );
	}

	@Override
	public Representation post( Representation entity ) throws ResourceException
	{
		return execute( entity, null );
	}

	@Override
	public Representation post( Representation entity, Variant variant ) throws ResourceException
	{
		return execute( entity, variant );
	}

	@Override
	public Representation put( Representation entity ) throws ResourceException
	{
		return execute( entity, null );
	}

	@Override
	public Representation put( Representation entity, Variant variant ) throws ResourceException
	{
		return execute( entity, variant );
	}

	@Override
	public Representation delete() throws ResourceException
	{
		return execute( null, null );
	}

	@Override
	public Representation delete( Variant variant ) throws ResourceException
	{
		return execute( null, variant );
	}

	@Override
	public Representation options() throws ResourceException
	{
		return execute( null, null );
	}

	@Override
	public Representation options( Variant variant ) throws ResourceException
	{
		return execute( null, variant );
	}

	// //////////////////////////////////////////////////////////////////////////
	// Private

	/**
	 * Constant.
	 */
	private static final String SOURCE = "source";

	/**
	 * Constant.
	 */
	private static final String HIGHLIGHT = "highlight";

	/**
	 * Constant.
	 */
	private static final String TRUE = "true";

	/**
	 * The {@link LanguageManager} used to create the language adapters.
	 */
	private volatile LanguageManager languageManager;

	/**
	 * The {@link DocumentSource} used to fetch scripts.
	 */
	private volatile DocumentSource<Executable> documentSource;

	/**
	 * Executables might use this directory for including fragments.
	 */
	private volatile File fragmentDirectory;

	/**
	 * Executables might use this directory for importing libraries.
	 */
	private volatile File libraryDirectory;

	/**
	 * If the URL points to a directory rather than a file, and that directory
	 * contains a file with this name, then it will be used.
	 */
	private volatile String defaultName;

	/**
	 * The default language tag to be used if the executable doesn't specify
	 * one.
	 */
	private volatile String defaultLanguageTag;

	/**
	 * The default cache key to use if the executable doesn't specify one.
	 */
	private volatile String defaultCacheKey;

	/**
	 * The default character set to be used if the client does not specify it.
	 */
	private volatile CharacterSet defaultCharacterSet;

	/**
	 * An optional {@link ExecutionController} to be used with the scripts.
	 */
	private volatile ExecutionController executionController;

	/**
	 * Whether or not trailing slashes are required for all requests.
	 */
	private volatile Boolean trailingSlashRequired;

	/**
	 * Whether or not to send information to the client about cache expiration.
	 */
	private volatile Integer clientCachingMode;

	/**
	 * Whether to prepare executables.
	 */
	private volatile Boolean prepare;

	/**
	 * This is so we can see the source code for scripts by adding
	 * <code>?source=true</code> to the URL.
	 */
	private volatile Boolean sourceViewable;

	/**
	 * Cache used for caching mode.
	 */
	private volatile Cache cache;

	/**
	 * The name of the global variable with which to access the document.
	 */
	private volatile String exposedDocumentName;

	/**
	 * The name of the global variable with which to access the application.
	 */
	private volatile String exposedApplicationName;

	/**
	 * The name of the global variable with which to access the conversation.
	 */
	private volatile String exposedConversationName;

	/**
	 * The document formatter.
	 */
	private volatile DocumentFormatter<Executable> documentFormatter;

	/**
	 * Does the actual handling of requests.
	 * 
	 * @param entity
	 *        The entity
	 * @param variant
	 *        The variant
	 * @return A representation
	 * @throws ResourceException
	 */
	private Representation execute( Representation entity, Variant variant ) throws ResourceException
	{
		Request request = getRequest();
		String documentName = request.getResourceRef().getRemainingPart( true, false );
		documentName = validateDocumentName( documentName );

		try
		{
			if( isSourceViewable() )
			{
				Form query = request.getResourceRef().getQueryAsForm();
				if( TRUE.equals( query.getFirstValue( SOURCE ) ) )
				{
					int lineNumber = -1;
					String line = query.getFirstValue( HIGHLIGHT );
					if( line != null )
					{
						try
						{
							lineNumber = Integer.parseInt( line );
						}
						catch( NumberFormatException x )
						{
						}
					}

					DocumentDescriptor<Executable> documentDescriptor = getDocumentSource().getDocument( documentName );
					DocumentFormatter<Executable> documentFormatter = getDocumentFormatter();
					if( documentFormatter != null )
						return new StringRepresentation( documentFormatter.format( documentDescriptor, documentName, lineNumber ), MediaType.TEXT_HTML );
					else
						return new StringRepresentation( documentDescriptor.getSourceCode() );
				}
			}

			ExecutionContext executionContext = new ExecutionContext();
			File libraryDirectory = getLibraryDirectory();
			if( libraryDirectory != null )
				executionContext.getLibraryLocations().add( libraryDirectory.toURI() );
			ExposedDocumentForGeneratedTextResource exposedContainer = new ExposedDocumentForGeneratedTextResource( this, executionContext, entity, variant );
			Representation representation = null;
			try
			{
				// Execute and represent output
				representation = exposedContainer.include( documentName );

				if( representation == null )
					throw new ResourceException( Status.CLIENT_ERROR_NOT_FOUND );
				else
				{
					switch( getClientCachingMode() )
					{
						case CLIENT_CACHING_MODE_DISABLED:
						{
							// Remove all caching headers
							representation.setModificationDate( null );
							representation.setExpirationDate( null );
							representation.setTag( null );
							List<CacheDirective> cacheDirectives = getResponse().getCacheDirectives();
							cacheDirectives.clear();
							cacheDirectives.add( CacheDirective.noCache() );
							break;
						}

						case CLIENT_CACHING_MODE_CONDITIONAL:
							// Leave conditional headers intact
							break;

						case CLIENT_CACHING_MODE_OFFLINE:
						{
							// Add offline caching headers based on conditional
							// headers
							Date expirationDate = representation.getExpirationDate();
							if( expirationDate != null )
							{
								long maxAge = ( expirationDate.getTime() - System.currentTimeMillis() );
								if( maxAge > 0 )
								{
									List<CacheDirective> cacheDirectives = getResponse().getCacheDirectives();
									cacheDirectives.clear();
									cacheDirectives.add( CacheDirective.maxAge( (int) maxAge / 1000 ) );
								}
							}
							break;
						}
					}

					return representation;
				}
			}
			catch( ParsingException x )
			{
				throw new ResourceException( x );
			}
			catch( ExecutionException x )
			{
				if( getResponse().getStatus().isSuccess() )
					// An unintended exception
					throw new ResourceException( x );
				else
					// This was an intended exception, so we will preserve the
					// status code
					return null;
			}
			finally
			{
				executionContext.release();
			}
		}
		catch( DocumentNotFoundException x )
		{
			throw new ResourceException( Status.CLIENT_ERROR_NOT_FOUND, x );
		}
		catch( DocumentException x )
		{
			throw new ResourceException( x );
		}
		catch( IOException x )
		{
			throw new ResourceException( x );
		}
	}
}