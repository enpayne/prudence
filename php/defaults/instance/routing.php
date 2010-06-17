<?php
//
// Prudence Routing
//

global $component, $application_name, $application_internal_name, $application_logger_name, $application_base_path, $application_default_url, $application_instance;

import java.io.File;
import java.util.ArrayList;
import com.threecrickets.prudence.util.IoUtil;

// Hosts

execute_or_default('instance/hosts/');

// Applications

$applications = new ArrayList();
$component->context->attributes['com.threecrickets.prudence.applications'] = $applications;
$applications_dir = new File('applications');

$properties_file = new File($applications_dir, 'applications.properties');
$properties = IoUtil::loadProperties($properties_file);
$save_properties = FALSE;
$application_files = $applications_dir->listFiles();
foreach($application_files as $application_file) {
	if(!$application_file->directory && substr($application_file->name, -4) == '.zip' && $properties->getProperty($application_file->name, '') != $application_file->lastModified()) {
		print 'Unpacking "' . $application_file->name . '"...' . "\n";
		IoUtil::unzip($application_file, $applications_dir);
		$properties->setProperty($application_file->name, $application_file->lastModified());
		$save_properties = TRUE;
	}
}
if($save_properties) {
	IoUtil::saveProperties($properties, $properties_file);
}

$application_dirs = $applications_dir->listFiles();
foreach($application_dirs as $application_dir) {
	if($application_dir->directory) {
		$application_name = $application_dir->name;
		$application_internal_name = $application_dir->name;
		$application_logger_name = $application_dir->name;
		$application_base_path = $application_dir->path;
		$application_default_url = '/' . $application_dir->name . '/';
		execute_or_default($application_base_path, 'defaults/application');
		$applications->add($application_instance);
	}
}

if($applications->empty) {
	print "No applications found. Exiting.\n";
	flush();
	$executable->context->writer->flush();
	System::exit(0);
}
?>