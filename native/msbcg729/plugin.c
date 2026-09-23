/*
 * RRP Softphone — G.729 para o Android.
 *
 * O SDK do liblinphone publicado no Maven vem sem o bcg729, e no mediastreamer2
 * o filtro G.729 (g729.c, copiado sem alterações do mediastreamer2 5.5.18) é
 * compilado para dentro da própria biblioteca. Em vez de recompilar o SDK
 * inteiro, esta biblioteca junta o g729.c e o bcg729 numa .so à parte e
 * registra os dois filtros na fábrica de mídia do Core já criado.
 *
 * Isso tem de acontecer antes de a primeira chamada montar o stream de áudio.
 * A lista de codecs do Core é montada na criação, então o app cria o Core com
 * "dont_check_codecs" ligado (ver LinphoneManager.kt): o payload G729 entra na
 * lista mesmo antes de o filtro existir, e quem tira o codec da oferta, se o
 * registro abaixo falhar, é o próprio app.
 *
 * Distribuído sob a GNU GPL v3 (ou, a critério de quem recebe, AGPL v3 — a
 * licença do g729.c), junto com o restante do app.
 */
#include <jni.h>

#include "mediastreamer2/msfactory.h"
#include "mediastreamer2/msfilter.h"

extern MSFilterDesc ms_bcg729_enc_desc;
extern MSFilterDesc ms_bcg729_dec_desc;

/* Da API C do liblinphone; declarado aqui para não trazer os headers inteiros. */
extern MSFactory *linphone_core_get_ms_factory(void *core);

JNIEXPORT jboolean JNICALL Java_com_rrpsystems_rrphone_core_sip_G729_register(JNIEnv *env, jclass clazz,
                                                                              jlong corePointer) {
	(void)env;
	(void)clazz;
	if (corePointer == 0) return JNI_FALSE;
	MSFactory *factory = linphone_core_get_ms_factory((void *)(intptr_t)corePointer);
	if (factory == NULL) return JNI_FALSE;
	/* Idempotente: o Core pode ser recriado, mas a fábrica é dele. */
	if (!ms_factory_has_encoder(factory, "G729")) {
		ms_factory_register_filter(factory, &ms_bcg729_enc_desc);
		ms_factory_register_filter(factory, &ms_bcg729_dec_desc);
	}
	return ms_factory_has_encoder(factory, "G729") && ms_factory_has_decoder(factory, "G729") ? JNI_TRUE : JNI_FALSE;
}
