/**
 * Copyright 2009-2011 Three Crickets LLC.
 * <p>
 * The contents of this file are subject to the terms of the LGPL version 3.0:
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * <p>
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly from Three Crickets
 * at http://threecrickets.com/
 */

package com.threecrickets.prudence.service;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.data.MediaType;

import com.threecrickets.prudence.ApplicationTask;
import com.threecrickets.prudence.DelegatedResource;
import com.threecrickets.prudence.GeneratedTextResource;
import com.threecrickets.prudence.util.LoggingUtil;
import com.threecrickets.scripturian.exception.DocumentException;
import com.threecrickets.scripturian.exception.ParsingException;

/**
 * Application service exposed to executables.
 * 
 * @author Tal Liron
 * @see DelegatedResource
 * @see GeneratedTextResource
 */
public class ApplicationService
{
	//
	// Construction
	//

	/**
	 * Constructor using the current application.
	 * 
	 * @see Application#getCurrent()
	 */
	public ApplicationService()
	{
		this( Application.getCurrent() );
	}

	/**
	 * Constructor.
	 * 
	 * @param application
	 *        The application
	 */
	public ApplicationService( Application application )
	{
		this.application = application;
	}

	//
	// Attributes
	//
	/**
	 * The underlying application.
	 * 
	 * @return The application
	 */
	public Application getApplication()
	{
		return application;
	}

	/**
	 * The underlying component.
	 * <p>
	 * Note: for this to work, the component must have been explicitly set as
	 * attribute <code>com.threecrickets.prudence.component</code> in the
	 * application's context.
	 * 
	 * @return The component
	 */
	public Component getComponent()
	{
		return (Component) getGlobals().get( "com.threecrickets.prudence.component" );
	}

	/**
	 * A map of all values global to the current application.
	 * 
	 * @return The globals
	 */
	public ConcurrentMap<String, Object> getGlobals()
	{
		return getApplication().getContext().getAttributes();
	}

	/**
	 * Gets a value global to the current application, atomically setting it to
	 * a default value if it doesn't exist.
	 * 
	 * @param name
	 *        The name of the global
	 * @param defaultValue
	 *        The default value
	 * @return The global's current value
	 */
	public Object getGlobal( String name, Object defaultValue )
	{
		ConcurrentMap<String, Object> globals = getGlobals();
		Object value = globals.get( name );

		if( defaultValue != null )
		{
			value = defaultValue;
			Object existing = globals.putIfAbsent( name, value );
			if( existing != null )
				value = existing;
		}
		else
			globals.remove( name );

		return value;
	}

	/**
	 * A map of all values global to all running applications.
	 * <p>
	 * Note that this could be null if shared globals are not set up.
	 * 
	 * @return The shared globals or null
	 * @see #getComponent()
	 */
	public ConcurrentMap<String, Object> getSharedGlobals()
	{
		Component component = getComponent();
		if( component != null )
			return component.getContext().getAttributes();
		else
			return null;
	}

	/**
	 * Gets a value global to all running applications, atomically setting it to
	 * a default value if it doesn't exist.
	 * <p>
	 * If shared globals are not set up, does nothing and returns null.
	 * 
	 * @param name
	 *        The name of the shared global
	 * @param defaultValue
	 *        The default value
	 * @return The shared global's current value
	 * @see #getComponent()
	 */
	public Object getSharedGlobal( String name, Object defaultValue )
	{
		ConcurrentMap<String, Object> globals = getSharedGlobals();

		if( globals == null )
			return null;

		Object value = globals.get( name );

		if( value == null )
		{
			if( defaultValue != null )
			{
				value = defaultValue;
				Object existing = globals.putIfAbsent( name, value );
				if( existing != null )
					value = existing;
			}
			else
				globals.remove( name );
		}

		return value;
	}

	/**
	 * The application's logger.
	 * 
	 * @return The logger
	 * @see #getSubLogger(String)
	 */
	public Logger getLogger()
	{
		if( logger == null )
			logger = LoggingUtil.getLogger( application );

		return logger;
	}

	/**
	 * A logger with a name appended with a "." to the application's logger
	 * name. This allows inheritance of configuration.
	 * 
	 * @param name
	 *        The sub-logger name
	 * @return The logger
	 * @see #getLogger()
	 */
	public Logger getSubLogger( String name )
	{
		return LoggingUtil.getSubLogger( getLogger(), name );
	}

	/**
	 * Get a media type by its MIME type name.
	 * 
	 * @param name
	 *        The MIME type name
	 * @return The media type
	 */
	public MediaType getMediaType( String name )
	{
		MediaType mediaType = MediaType.valueOf( name );
		if( mediaType == null )
			mediaType = getApplication().getMetadataService().getMediaType( name );
		return mediaType;
	}

	/**
	 * Gets the shared executor service, creating one if it doesn't exist.
	 * <p>
	 * If shared globals are not set up, gets the application's executor
	 * service.
	 * <p>
	 * This setting can be configured by setting an attribute named
	 * <code>com.threecrickets.prudence.executor</code> in the component's
	 * {@link Context}.
	 * 
	 * @return The executor service
	 */
	public ExecutorService getExecutor()
	{
		if( executor == null )
		{
			ConcurrentMap<String, Object> attributes = getSharedGlobals();

			if( attributes != null )
			{
				executor = (ExecutorService) attributes.get( "com.threecrickets.prudence.executor" );

				if( executor == null )
				{
					executor = Executors.newScheduledThreadPool( Runtime.getRuntime().availableProcessors() * 2 + 1 );

					ExecutorService existing = (ExecutorService) attributes.putIfAbsent( "com.threecrickets.prudence.executor", executor );
					if( existing != null )
						executor = existing;
				}
			}
			else
				executor = application.getTaskService();
		}

		return executor;
	}

	//
	// Operations
	//

	/**
	 * Submits or schedules an {@link ApplicationTask} on the the shared
	 * executor service.
	 * 
	 * @param documentName
	 *        The document name
	 * @param delay
	 *        Initial delay in milliseconds, or zero for ASAP
	 * @param repeatEvery
	 *        Repeat delay in milliseconds, or zero for no repetition
	 * @param fixedRepeat
	 *        Whether repetitions are at fixed times, or if the repeat delay
	 *        begins when the task ends
	 * @return A future for the task
	 * @throws ParsingException
	 * @throws DocumentException
	 * @see #getExecutor()
	 */
	public Future<?> task( String documentName, int delay, int repeatEvery, boolean fixedRepeat ) throws ParsingException, DocumentException
	{
		ExecutorService executor = getExecutor();
		if( ( delay > 0 ) || ( repeatEvery > 0 ) )
		{
			if( !( executor instanceof ScheduledExecutorService ) )
				throw new RuntimeException( "Executor must implement the ScheduledExecutorService interface to allow for delayed tasks" );

			ScheduledExecutorService scheduledExecutor = (ScheduledExecutorService) executor;
			if( repeatEvery > 0 )
			{
				if( fixedRepeat )
					return scheduledExecutor.scheduleAtFixedRate( new ApplicationTask( application, documentName ), delay, repeatEvery, TimeUnit.MILLISECONDS );
				else
					return scheduledExecutor.scheduleWithFixedDelay( new ApplicationTask( application, documentName ), delay, repeatEvery, TimeUnit.MILLISECONDS );
			}
			else
				return scheduledExecutor.schedule( new ApplicationTask( application, documentName ), delay, TimeUnit.MILLISECONDS );
		}
		else
			return executor.submit( new ApplicationTask( application, documentName ) );
	}

	// //////////////////////////////////////////////////////////////////////////
	// Private

	/**
	 * The application.
	 */
	private final Application application;

	/**
	 * The executor service.
	 */
	private ExecutorService executor;

	/**
	 * The logger.
	 */
	private Logger logger;
}
