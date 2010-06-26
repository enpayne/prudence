
document.execute('defaults/application/routing/')

from com.threecrickets.prudence.util import CssUnifyMinifyFilter, JavaScriptUnifyMinifyFilter

router.capture(resources_base_url + 'data/note/{id}/', 'data/note/')
router.hide('data/note/')

# Wrap the static web with unify-minify filters
router.detach(static_web)
wrapped_static_web = CssUnifyMinifyFilter(application_instance.context, static_web, File(application_base_path + static_web_base_path), dynamic_web_minimum_time_between_validity_checks)
wrapped_static_web = JavaScriptUnifyMinifyFilter(application_instance.context, wrapped_static_web, File(application_base_path + static_web_base_path), dynamic_web_minimum_time_between_validity_checks)
router.attachBase(static_web_base_url, wrapped_static_web)
