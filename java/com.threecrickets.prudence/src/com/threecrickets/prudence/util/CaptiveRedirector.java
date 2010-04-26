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

package com.threecrickets.prudence.util;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Reference;
import org.restlet.routing.Redirector;

/**
 * A {@link Redirector} that keeps track of the captured reference.
 * 
 * @author Tal Liron
 */
public class CaptiveRedirector extends Redirector
{
	//
	// Constants
	//

	/**
	 * Request attribute of the captive {@link Reference}.
	 * 
	 * @see #getCaptiveReference(Request)
	 * @see #setCaptiveReference(Request, Reference)
	 */
	public static final String CAPTIVE_REFERENCE = "com.threecrickets.prudence.util.CaptiveRedirector.captiveReference";

	//
	// Static attributes
	//

	/**
	 * The captive reference.
	 * 
	 * @param request
	 *        The request
	 * @return The captured reference
	 * @see #setCaptiveReference(Request, Reference)
	 */
	public static Reference getCaptiveReference( Request request )
	{
		return (Reference) request.getAttributes().get( CAPTIVE_REFERENCE );
	}

	/**
	 * The captive reference.
	 * 
	 * @param request
	 *        The request
	 * @param captiveReference
	 *        The captive reference
	 * @see #getCaptiveReference(Request)
	 */
	public static void setCaptiveReference( Request request, Reference captiveReference )
	{
		request.getAttributes().put( CAPTIVE_REFERENCE, captiveReference );
	}

	//
	// Construction
	//

	/**
	 * Construction for {@link Redirector#MODE_SERVER_OUTBOUND}.
	 * 
	 * @param context
	 *        The context
	 * @param targetTemplate
	 *        The target template
	 * @param root
	 *        Whether to set the base reference to the root URI
	 */
	public CaptiveRedirector( Context context, String targetTemplate, boolean root )
	{
		this( context, targetTemplate, root, MODE_SERVER_OUTBOUND );
	}

	/**
	 * Construction.
	 * 
	 * @param context
	 *        The context
	 * @param targetTemplate
	 *        The target template
	 * @param mode
	 *        The redirection mode
	 * @param root
	 *        Whether to set the base reference to the root URI
	 */
	public CaptiveRedirector( Context context, String targetPattern, boolean root, int mode )
	{
		super( context, targetPattern, mode );
		this.root = root;
		setOwner( "Prudence" );
		setAuthor( "Tal Liron" );
		setName( "CaptiveRedirector" );
		setDescription( "Redirector that keeps track of the captive reference" );
	}

	//
	// Redirector
	//

	@Override
	public void handle( Request request, Response response )
	{
		Reference captiveReference = root ? new Reference( request.getHostRef(), request.getResourceRef() ) : new Reference( request.getResourceRef() );
		setCaptiveReference( request, captiveReference );
		super.handle( request, response );
	}

	private final boolean root;
}