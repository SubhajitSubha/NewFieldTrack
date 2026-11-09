# Translations

Nine languages ship: English and, in the first wave, Hindi (`hi`), Spanish (`es`), Portuguese
(`pt`, Brazilian), French (`fr`), Indonesian (`in`, the old folder code for `id`), Swahili (`sw`),
Bengali (`bn`) and Arabic (`ar`, right to left).

**Every one is a draft.** Each was written here and checked by machine for the things that crash
(`TranslationsTest`: the same strings, the same blanks in each, every plural form its language
needs, no bare apostrophes), but **none has been read by a native speaker**, and the code comment at
the top of each file says so. `AppLanguage.SUPPORTED` lists the language, so a person can choose it;
decide before release whether to keep a language listed until somebody who speaks it has read it.

## What a reader should look at first
These came back from the translators as the entries they were least sure of.

- **All languages:** the help answers (`help_a_*`) name buttons and screens; check each name against
  the button actually shown in that language. `plan_warn_machine_booked` / `plan_warn_driver_booked`
  fill two blanks whose order and kind (a part of the day, a name) the translators could not see.
  `team_perm_people_cost_note` ("cannot be un-seen") is an idiom none could render exactly.
  Machine papers (`paper_fitness`, `paper_pollution`) are regional ideas; the nearest local word was used.
  Equipment with no settled word (mulcher, ridger, power tiller, transplanter, baler, harrow,
  grain cart) was described.
- **Land units** (bigha, kanal, guntha, katha) and UPI / GSTIN were kept as the names they are.
  The sample names (customers, farms, addresses) were localised.
- **Hindi:** `onboard_point_start` ("one tap to start") is unnatural; weekday initials are two
  letters for some days; "बेड़ा" for the fleet may be better as "मशीनें".
- **Spanish / Portuguese / French:** gender and "you" in sentences about a driver or a machine
  (`team_*`, `invites_*`); French runs long in narrow places (`status_added_by_hand`, `team_perm_*`).
  Portuguese "fiado" for on credit is Brazilian; `doc_kind_tax_invoice` deliberately avoids "Nota fiscal".
- **Indonesian:** "rupiah" was written where English says "rupee" in two sentences; the
  statement is "rekap tagihan" (not a bank statement).
- **Swahili:** weekday initials repeat (J, J, J, A, I, J, J); equipment words are descriptive;
  plan names "Bure" and "Biashara" are the translator's choice.
- **Bengali:** words differ between Bangladesh and West Bengal (water, the land units); the fleet
  tab is "বহর" to fit; greeting is the neutral "হ্যালো".
- **Arabic:** plural forms are all six; "عدّاد" means both the work timer and the machine's hour
  meter (the meter is "عدّاد الساعات"); units `km`/`kg`/`L` stay Latin; check "مُطّلع" for the viewer role.

## Added with the field-feedback round (`strings_10.xml`, Indonesian `strings_11.xml`)
128 more entries, drafted the same way and equally unread. Read first: the words for **role**
(`roles_*`, a named bundle of what a team member may do), **hire** (`hire_*`, `owner_*`: money owed to
the owner of a machine the business does not own), **taken back** (`person_pay_*`: a payment cancelled
but kept in the record), the **I need help** sheet (`sos_*`) and the notification lines
(`push_breakdown_*`, `push_sos`, `push_member_joined`, which take a machine's or a person's name, so
check gender and word order). Arabic uses masculine verbs in the notification lines.

## Added with the rearrangement round (`strings_11.xml`, Indonesian `strings_12.xml`)
61 more entries for the new menu, Settings, the profile form, search in the bar, the Money page and the
team pages, drafted the same way and equally unread. Read first: **Change** on the business line of the
menu (`menu_change_business`, a short verb on a small pill, so it must stay short), the question before
adding a person (`team_add_how`, `team_add_invite`, `team_add_write`: "will they use the app on their own
phone?"), the member page's parts (`member_tab_*`; "What they can do" must fit beside two short words),
**take this back** for a payment (`person_pay_back_button`, which cancels but keeps the record), and the
two lines that say what a role may not see (`mh_hidden_*`). Spanish, Portuguese and French refer to the
team member as "she/elle/ela" in `team_add_*` where the word is "persona/pessoa/personne"; check it reads
naturally.

## Added with the trust-and-money round (`strings_12.xml`, Indonesian `strings_13.xml`)
59 more entries for cash in hand, a person's particulars and pay terms, My pay, and UPI, drafted the
same way and unread. Read first: **cash handed over** (`member_cash_*`, `mh_k_handover`: a driver giving
the business's cash to the owner, not a payment to anybody), the **pay basis** words (`pay_basis_*`,
`wages_per_*`), **take back / taken back by** (`mh_taken_back_by`), and the UPI lines (`upi_*`,
`payout_upi_*`), which are only ever shown in India, so the Hindi and Bengali matter most there.

## Added with the work-follows-the-machine round (`strings_13.xml`, Indonesian `strings_14.xml`)
18 more entries, drafted the same way and unread. Read first: the new kinds of work (`work_type_puddling`, wet tillage
for rice; `work_type_hire`, a machine and its driver for the day), **What is attached? / Pulled by / Not one of ours**
(`work_attached`, `work_pulled_by`, `work_pulled_none`: the tool behind a tractor, and the tractor in front of a tool,
including a farmer's own), and the boxes that name an "Other" (`work_other_name*`, `paper_other_name`,
`method_other_name`), whose note must use the same word for "Other" as the chip above it.

Land units added with the same round: decimal, cent, marla, are, dunam, feddan, rai, mu, manzana, tarea,
alqueire, and the business's own ("Local unit"). **Arabic:** the acre had been written «فدان», which is the feddan, a
different and slightly larger unit; now that both are offered the acre is «إيكر». Check that word, and that the
Bengali decimal reads as «শতক».

## Things the translations cannot yet do
- The amount in words at the foot of a bill is English-only (and only for rupees). A bill in
  another language leaves that line off rather than print English number-words.
- Numbers are written with the digits 0 to 9 in every language so that they match the money; a
  person who wants Eastern Arabic or Bengali digits cannot ask for them yet.
- The server's public booking page and its messages are English.
- Reminders to a customer are written in the customer's language (this existed), and a bill is
  written in the language of the business, so a page never mixes two.
