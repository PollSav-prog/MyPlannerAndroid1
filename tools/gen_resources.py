import json, re, os, math, wave, struct
SRC = '/home/claude/MyPlanner/src/MyPlanner/Locales'
RES = '/home/claude/MyPlannerAndroid/app/src/main/res'
EXTRA = {
 'ru': {
  'theme_system_a':'Как в системе','tts_no_voice_a':'Для этого языка в телефоне нет голоса озвучивания. Установите его в настройках «Синтез речи».',
  'perm_notif':'Разрешите уведомления — иначе напоминания не придут.','btn_allow':'Разрешить','btn_later':'Позже',
  'pick_date':'Выбрать дату','pick_time':'Выбрать время','lbl_date':'Дата','lbl_time':'Время',
  'voice_no_service':'На телефоне нет службы распознавания речи. Введите фразу текстом.',
  'speech_android':'Распознавание выполняет служба речи вашего телефона (обычно Google). Для некоторых языков нужен интернет.',
  'backup_saved':'Копия сохранена.','sound_volume_note':'Громкость сигнала задаётся системной громкостью уведомлений.',
  'notif_channel_due':'Напоминания','notif_channel_warn':'Предупреждения о событиях','notif_channel_silent':'Напоминания без звука',
  'set_sound_pick':'Мелодия сигнала','lbl_priority_short':'Приоритет','btn_pick':'Выбрать','lbl_none':'Без категории'},
 'en': {
  'theme_system_a':'Same as system','tts_no_voice_a':'There is no text-to-speech voice for this language on your phone. Install one in the "Text-to-speech" settings.',
  'perm_notif':'Allow notifications, otherwise reminders will not arrive.','btn_allow':'Allow','btn_later':'Later',
  'pick_date':'Choose date','pick_time':'Choose time','lbl_date':'Date','lbl_time':'Time',
  'voice_no_service':'There is no speech recognition service on this phone. Type the phrase instead.',
  'speech_android':'Recognition is done by your phone\'s speech service (usually Google). Some languages need internet.',
  'backup_saved':'Backup saved.','sound_volume_note':'The signal volume is the system notification volume.',
  'notif_channel_due':'Reminders','notif_channel_warn':'Event warnings','notif_channel_silent':'Silent reminders',
  'set_sound_pick':'Signal sound','lbl_priority_short':'Priority','btn_pick':'Choose','lbl_none':'No category'},
 'de': {
  'theme_system_a':'Wie im System','tts_no_voice_a':'Für diese Sprache ist auf dem Telefon keine Sprachausgabe installiert. Installiere sie in den Einstellungen „Sprachausgabe“.',
  'perm_notif':'Erlaube Benachrichtigungen, sonst kommen keine Erinnerungen an.','btn_allow':'Erlauben','btn_later':'Später',
  'pick_date':'Datum wählen','pick_time':'Zeit wählen','lbl_date':'Datum','lbl_time':'Zeit',
  'voice_no_service':'Auf diesem Telefon gibt es keinen Spracherkennungsdienst. Gib den Satz als Text ein.',
  'speech_android':'Die Erkennung übernimmt der Sprachdienst deines Telefons (meist Google). Für manche Sprachen ist Internet nötig.',
  'backup_saved':'Sicherung gespeichert.','sound_volume_note':'Die Lautstärke entspricht der System-Benachrichtigungslautstärke.',
  'notif_channel_due':'Erinnerungen','notif_channel_warn':'Ereigniswarnungen','notif_channel_silent':'Stille Erinnerungen',
  'set_sound_pick':'Signalton','lbl_priority_short':'Priorität','btn_pick':'Wählen','lbl_none':'Keine Kategorie'},
 'fr': {
  'theme_system_a':'Comme le système','tts_no_voice_a':'Aucune voix de synthèse vocale pour cette langue sur votre téléphone. Installez-en une dans les réglages « Synthèse vocale ».',
  'perm_notif':'Autorisez les notifications, sinon les rappels n\'arriveront pas.','btn_allow':'Autoriser','btn_later':'Plus tard',
  'pick_date':'Choisir la date','pick_time':'Choisir l\'heure','lbl_date':'Date','lbl_time':'Heure',
  'voice_no_service':'Aucun service de reconnaissance vocale sur ce téléphone. Saisissez la phrase.',
  'speech_android':'La reconnaissance est faite par le service vocal du téléphone (souvent Google). Certaines langues nécessitent Internet.',
  'backup_saved':'Sauvegarde enregistrée.','sound_volume_note':'Le volume du signal est celui des notifications du système.',
  'notif_channel_due':'Rappels','notif_channel_warn':'Avertissements d\'événements','notif_channel_silent':'Rappels silencieux',
  'set_sound_pick':'Son du signal','lbl_priority_short':'Priorité','btn_pick':'Choisir','lbl_none':'Sans catégorie'},
}
def esc(v):
    v = v.replace('\\','\\\\').replace("'", "\\'").replace('"','\\"').replace('&','&amp;').replace('<','&lt;').replace('\n','\\n')
    if v[:1] in '@?': v = '\\' + v
    v = re.sub(r'\{(\d)\}', lambda m: '%%%d$s' % (int(m.group(1))+1), v)
    return v
for code, folder in [('ru','values'),('en','values-en'),('de','values-de'),('fr','values-fr')]:
    fn = {'ru':'ru-RU','en':'en-US','de':'de-DE','fr':'fr-FR'}[code]
    d = json.load(open(f'{SRC}/{fn}.json', encoding='utf-8'))
    d.update(EXTRA[code])
    lines = ['<?xml version="1.0" encoding="utf-8"?>','<resources>']
    for k,v in d.items():
        if k.startswith('_'): continue
        assert '%' not in v, (k, v)
        lines.append(f'    <string name="{k}">{esc(v)}</string>')
    lines.append('</resources>')
    open(f'{RES}/{folder}/strings.xml','w',encoding='utf-8').write('\n'.join(lines)+'\n')
    print(folder, len(d))

# ---- sounds ----
SR = 22050
def write(name, samples):
    peak = max(abs(s) for s in samples) or 1
    with wave.open(f'{RES}/raw/{name}.wav','wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b''.join(struct.pack('<h', int(s/peak*0.8*32767)) for s in samples))
def tone(freq, dur, decay, partials=((1,1.0),), attack=0.01):
    n = int(SR*dur); out=[]
    for i in range(n):
        t=i/SR; env=min(1,t/attack)*math.exp(-decay*t)
        out.append(env*sum(a*math.sin(2*math.pi*freq*p*t) for p,a in partials))
    return out
def mix(parts, total):
    buf=[0.0]*int(SR*total)
    for start, samp in parts:
        o=int(start*SR)
        for i,s in enumerate(samp):
            if o+i<len(buf): buf[o+i]+=s
    return buf
# «тыдын — тыдын»: две мягкие ноты, дважды
soft = mix([(0.0,tone(659.25,1.0,4.5,((1,1),(2,0.25)))),(0.45,tone(523.25,1.2,4.0,((1,1),(2,0.2)))),
            (1.3,tone(659.25,1.0,4.5,((1,1),(2,0.25)))),(1.75,tone(523.25,1.4,4.0,((1,1),(2,0.2))))], 3.2)
bell = mix([(0.0,tone(880,1.8,2.8,((1,1),(2.76,0.5),(5.4,0.25)))),(0.7,tone(660,2.0,2.6,((1,1),(2.76,0.5),(5.4,0.25))))], 2.8)
mar = mix([(0.0,tone(523.25,0.7,7,((1,1),(4,0.3)),0.003)),(0.3,tone(392,0.7,7,((1,1),(4,0.3)),0.003)),
           (1.0,tone(523.25,0.7,7,((1,1),(4,0.3)),0.003)),(1.3,tone(392,0.9,7,((1,1),(4,0.3)),0.003))], 2.4)
write('soft',soft); write('bell',bell); write('marimba',mar)
