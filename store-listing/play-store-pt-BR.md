# Play Store — RRP Softphone (rascunho)

Textos e respostas para o Play Console. Revisar antes de enviar; os limites
de caracteres são os do Console.

## Ficha da loja

**Nome do app** (máx. 30): `RRP Softphone`

**Descrição curta** (máx. 80):

> Softphone SIP para o seu ramal: ligue, transfira e receba com o app fechado.

**Descrição completa** (máx. 4000):

> O RRP Softphone transforma o celular em um ramal do seu PABX. Faça e receba
> ligações pela conta SIP da sua empresa, com a mesma experiência do telefone
> de mesa — e do RRP Softphone para Windows.
>
> **Chamadas**
> • Receba ligações mesmo com o app fechado e a tela bloqueada
> • Integração com o sistema: tela de chamada, Bluetooth e fone de ouvido
> • Mudo, espera, teclado DTMF e alto-falante
> • Chamada em espera: atenda uma segunda ligação e alterne entre as duas
>
> **Transferência e conferência**
> • Transferência com consulta: fale com o destino antes de passar a ligação
> • Ou transfira direto, sem anúncio
> • Conferência a três, juntando as duas chamadas
>
> **Recursos do ramal**
> • Não perturbe e siga-me (encaminhamento para outro ramal)
> • Histórico de chamadas, incluindo as atendidas em outro aparelho
> • Agenda da empresa carregada do servidor, mais contatos próprios
>
> **Para quem administra**
> • Configuração por arquivo (.rrpprofile), compatível com a versão Windows
> • Codecs PCMA, PCMU, G.729 e OPUS; DTMF RFC 2833, SIP INFO ou in-band
> • UDP, TCP e TLS
>
> O app precisa de uma conta SIP (ramal) fornecida pela empresa responsável
> pelo seu PABX. Ele não cria contas nem oferece serviço de telefonia por
> conta própria.
>
> Código aberto, sob a licença GNU GPL v3:
> https://github.com/RRPSystems/rrphone-android

**Categoria:** Comunicação
**Tags sugeridas:** VoIP, SIP, Softphone, Ramal, PABX
**E-mail de contato:** contato@rrpsystems.com.br
**Site:** https://rrpsystems.github.io/rrphone-desktop/
**Política de privacidade:** https://rrpsystems.github.io/rrphone-desktop/privacidade.html
(publicar a página antes de enviar)

**Imagens**
- Ícone 512×512: `store-listing/icon-512.png` (gerado por `tools/make-icons.ps1`)
- Banner 1024×500: a fazer
- Capturas de tela (mín. 2): teclado, chamada em andamento, transferência,
  histórico, ajustes — de preferência de um celular real

## Conteúdo do app

**Acesso ao app** — "Todas ou algumas funcionalidades estão restritas":
> O app é um softphone SIP e precisa de um ramal para funcionar. Na tela
> inicial, informe: Servidor SIP `<servidor>:<porta>`, Ramal `<ramal>`,
> Senha `<senha>`, Transporte `<TCP>`. Para testar uma ligação, disque
> `<ramal de destino ou número de teste>`.

(Usar um ramal exclusivo para revisão, nunca um ramal de cliente.)

**Anúncios:** não contém anúncios.

**Classificação indicativa:** categoria "Utilitário, produtividade,
comunicação ou outro". Responder "não" a violência, sexo, drogas, jogos de
azar etc. O questionário pergunta se usuários interagem/trocam informações:
**sim** (chamadas de voz entre usuários). Resultado esperado: Livre.

**Público-alvo:** 18 anos ou mais. Não é direcionado a crianças.

**App de notícias:** não. **App governamental:** não. **Recursos financeiros:** nenhum.
**Saúde:** não é um app de saúde.

**Exclusão de conta:** o app não permite criar contas (o ramal vem do PABX
da empresa). Responder que não há criação de conta no app.

## Segurança dos dados

**O app coleta ou compartilha dados do usuário?** Sim.
**Os dados são criptografados em trânsito?** Sim (TLS para o servidor de push;
SIP/TLS quando configurado). Obs.: se a conta usar UDP/TCP sem TLS, a
sinalização com o PABX não é cifrada — declarar "sim" só se todas as conexões
enviadas pela RRP forem cifradas; na dúvida, revisar com o jurídico.
**O usuário pode pedir a exclusão dos dados?** Sim (Sair da conta / desinstalar;
contato@rrpsystems.com.br para o servidor de push).

| Tipo de dado (Play) | Coletado | Compartilhado | Finalidade | Opcional? |
|---|---|---|---|---|
| Áudio — gravações de voz ou som | Não armazenado; transmitido em tempo real durante a chamada | Não | Funcionalidade do app | Não |
| Identificadores do dispositivo ou outros IDs (token de push do Firebase) | Sim | Não (enviado ao servidor de push da própria RRP) | Funcionalidade do app | Sim (chave "Receber chamadas com o app fechado") |
| Informações do app e desempenho — registros de falha | Não | Não | — | — |
| Contatos | Não (a agenda vem do servidor da empresa e fica no aparelho) | Não | — | — |

Observação: o Play considera "coleta" o que sai do aparelho para um servidor
do desenvolvedor. O áudio e a sinalização vão para o PABX do cliente (terceiro
operado pela empresa do usuário) e o token para o push da RRP.

## Declarações de permissão

**Intent de tela cheia (USE_FULL_SCREEN_INTENT)**
> Aplicativo de chamadas. A tela cheia é usada exclusivamente para exibir uma
> ligação recebida quando o aparelho está bloqueado, como o discador nativo.

**Serviço em primeiro plano — tipo phoneCall (e microphone)**
> Mantém a ligação ativa enquanto o usuário está em uma chamada, mesmo fora
> da tela do app, com a notificação padrão de chamada em andamento (botão
> Desligar). O serviço começa quando a chamada é efetuada ou atendida e termina
> quando ela acaba. O microfone só é usado durante a chamada.

(O Console pode pedir um vídeo curto mostrando a funcionalidade: gravar uma
chamada em andamento, sair do app e mostrar a notificação de chamada.)

**Gerenciar chamadas próprias (MANAGE_OWN_CALLS)** — não exige declaração;
é a integração padrão de apps VoIP com o sistema.
