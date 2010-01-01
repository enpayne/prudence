package com.threecrickets.prudence.util;

import java.io.StringReader;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.restlet.Request;
import org.restlet.data.MediaType;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.ResourceException;

import com.threecrickets.scripturian.Document;
import com.threecrickets.scripturian.DocumentSource.DocumentDescriptor;

/**
 * Use <a href="http://pygments.org/">Pygments</a> over Jython to represent
 * source code.
 * 
 * @author Tal Liron
 */
public class PygmentsSourceRepresenter implements SourceRepresenter
{
	//
	// SourceFormatter
	//

	public Representation representSource( String name, DocumentDescriptor<Document> documentDescriptor, Request request ) throws ResourceException
	{
		String tag = documentDescriptor.getTag();
		String language = null;
		if( "py".equals( tag ) )
			language = "python";
		else if( "rb".equals( tag ) )
			language = "ruby";
		else if( "gv".equals( tag ) || "groovy".equals( tag ) )
			language = "groovy";
		else if( "js".equals( tag ) )
			language = "javascript";
		else if( "clj".equals( tag ) )
			language = "clojure";
		else if( "php".equals( tag ) )
			language = "html+php";
		else if( "html".equals( tag ) )
			language = "html";
		else if( "xhtml".equals( tag ) )
			language = "html";
		else if( "xml".equals( tag ) )
			language = "xml";
		else if( "xslt".equals( tag ) )
			language = "xslt";

		if( language == null )
			return new StringRepresentation( documentDescriptor.getText() );

		Container container = new Container( language, name, "vs", documentDescriptor.getText() );
		ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
		ScriptEngine scriptEngine = scriptEngineManager.getEngineByName( "python" );
		ScriptContext scriptContext = scriptEngine.getContext();
		scriptContext.setAttribute( "container", container, ScriptContext.ENGINE_SCOPE );

		if( scriptEngine instanceof Compilable )
		{
			synchronized( program )
			{
				if( compiledScript == null )
				{
					try
					{
						compiledScript = ( (Compilable) scriptEngine ).compile( program );
					}
					catch( ScriptException x )
					{
						throw new ResourceException( x );
					}
				}

				try
				{
					compiledScript.eval( scriptContext );
				}
				catch( ScriptException x )
				{
					throw new ResourceException( x );
				}
			}
		}
		else
		{
			try
			{
				scriptEngine.eval( new StringReader( program ) );
			}
			catch( ScriptException x )
			{
				throw new ResourceException( x );
				// return new StringRepresentation( documentDescriptor.getText()
				// );
			}
		}

		Representation representation = new StringRepresentation( container.getText() );
		representation.setMediaType( MediaType.TEXT_HTML );
		return representation;
	}

	public static class Container
	{
		public Container( String language, String title, String style, String text )
		{
			this.language = language;
			this.title = title;
			this.style = style;
			this.text = text;
		}

		public String getLanguage()
		{
			return language;
		}

		public String getTitle()
		{
			return title;
		}

		public String getStyle()
		{
			return style;
		}

		public String getText()
		{
			return text;
		}

		public void setText( String text )
		{
			this.text = text;
		}

		private final String language;

		private final String title;

		private final String style;

		private String text;
	}

	// //////////////////////////////////////////////////////////////////////////
	// Private

	private CompiledScript compiledScript;

	private final String program = "from pygments import highlight\n" + "from pygments.lexers import get_lexer_by_name\n" + "from pygments.formatters import HtmlFormatter\n"
		+ "lexer = get_lexer_by_name(container.language, stripall=True)\n" + "formatter = HtmlFormatter(full=True, linenos=True, title=container.title, style=container.style)\n"
		+ "container.text = highlight(container.text, lexer, formatter)\n";
}