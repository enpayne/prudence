<html>
<head>
	<title>PHP Template Example</title>
	<link rel="stylesheet" type="text/css" href="<?=php $conversation->base ?>/style/three-crickets/dark-cricket/dark-cricket.min.css" />
	<link href="<?= $conversation->reference->scheme ?>://fonts.googleapis.com/css?family=Ubuntu|Ubuntu:italic|Ubuntu:bold|Ubuntu:bolditalic" rel="stylesheet" type="text/css" />
</head>
<body>
<div id="main"><div id="main-content">
<h1>PHP Template Example</h1>
<table width="100%"><tr valign="top"><td>
<?

//
// Defer this page
//

if($_GET['defer'] == 'true') if($conversation->defer()) $conversation->stop();

//
// Cache this page
//

$caching->duration = 5000;

//
// Calling Java
//

import java.lang.System;
print '<p>This page was dynamically generated at ' . System::currentTimeMillis() . '</p>';

//
// An example of a function
//

function print_adapter($adapter) {
?>
<p>
	<i>Adapter:</i> <?= $adapter->attributes['name'] ?> version <?= $adapter->attributes['version'] ?><br />
	<i>Language:</i> <?= $adapter->attributes['language.name'] ?> version <?= $adapter->attributes['language.version'] ?><br />
	<i>Tags:</i> 
<?
	$tags = $adapter->attributes['tags']->toArray();
	for($i = 0; $i < count($tags); $i++) {
		print($tags[$i]);
		if($i < count($tags) - 1) {
			print ', ';
		}
	}
?>
</p>
<?
}
?>
<h3>Language used:</h3>
<?
print_adapter($executable->context->adapter);
?>
<h3>Available languages:</h3>
<?
$adapters = $executable->languageManager->adapters->toArray();
foreach($adapters as $adapter) {
	print_adapter($adapter);
}
?>
</td><td>
<h3>The "id" attribute in the URL query is:</h3>
<p><?= $_GET['id'] ?></p>
<h3>A few tests:</h3>
<p>
<?

//
// Including a document
//
// This is identical to:
//
//   $document->include('/triple/php/');
//

?>
<?& '/triple/php/' ?>
<?

for($i = 0; $i < 10; $i++) {
?>
A multiple of three: 
<?
	print_triple($i);
?>
<br />
<?
}
?>
</p>
</td></tr></table>
</div></div>
</body>
<html>