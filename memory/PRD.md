# Whatsup - Product Requirements Document

## Overview
**Whatsup** è l'app numero 1 in Italia per chi non sa cosa fare oggi. L'utente apre l'app, vede subito gli eventi vicino a lui, filtra per categoria e distanza, e decide in meno di 30 secondi.

## User Personas
1. **Marco, 28 anni** - Professionista che vuole trovare attività nel weekend
2. **Giulia, 35 anni** - Mamma che cerca eventi family-friendly
3. **Luca, 22 anni** - Studente in cerca di concerti ed eventi notturni

## Core Requirements (Static)
- Mappa eventi in tempo reale con pin colorati per categoria
- Filtri rapidi: oggi/domani/weekend, distanza (2/5/10km), categoria
- Lista eventi alternativa alla mappa
- Dettaglio evento con descrizione AI-enhanced
- RSVP e salvataggio eventi
- Profilo utente con storico eventi
- Google OAuth login (Emergent-managed)

## Technical Stack
- **Frontend**: React 19, Tailwind CSS, Shadcn/UI, Leaflet/OpenStreetMap
- **Backend**: FastAPI, MongoDB, Motor (async)
- **AI**: Claude Sonnet 4.5 (Emergent LLM Key) per riscrittura descrizioni
- **Auth**: Emergent-managed Google OAuth

## What's Been Implemented (MVP - Jan 2026)

### Backend APIs
- ✅ /api/events - Lista eventi con filtri (categoria, distanza, data)
- ✅ /api/events/{id} - Dettaglio evento
- ✅ /api/events/{id}/rsvp - RSVP evento
- ✅ /api/events/{id}/save - Salvataggio evento
- ✅ /api/auth/session - Google OAuth exchange
- ✅ /api/auth/me - Utente corrente
- ✅ /api/user/saved-events - Eventi salvati
- ✅ /api/user/rsvps - Eventi con RSVP
- ✅ /api/categories - Lista categorie con colori
- ✅ /api/ai/rewrite-description - Riscrittura AI
- ✅ /api/seed-events - Seed dati mock

### Frontend Pages
- ✅ HomePage - Mappa + lista eventi con filtri
- ✅ EventDetailPage - Dettaglio evento con RSVP
- ✅ ProfilePage - Profilo utente con eventi salvati
- ✅ AuthCallback - Gestione OAuth callback
- ✅ LandingPage - Pagina di presentazione

### Features
- ✅ Mappa Leaflet con marker colorati per categoria
- ✅ Filtri data (oggi/domani/weekend)
- ✅ Filtri distanza (2/5/10km/ovunque)
- ✅ Filtri categoria (multipli)
- ✅ Switch vista mappa/lista
- ✅ Geolocalizzazione automatica
- ✅ 16 eventi mock italiani (Roma, Milano, Firenze, Napoli, Torino, Bologna, Venezia)
- ✅ Design "Italian Neo-Pop Utility" con colori warm

## Prioritized Backlog

### P0 - Critical (Next Sprint)
- [ ] Integrazione reale Eventbrite API
- [ ] Integrazione reale Meetup API
- [ ] Push notifications per eventi salvati

### P1 - High Priority
- [ ] Chat evento (dopo RSVP)
- [ ] Sistema di recensioni eventi
- [ ] Ricerca testuale eventi
- [ ] Multi-lingua (EN, DE, FR, ES)

### P2 - Medium Priority
- [ ] App mobile nativa (React Native)
- [ ] Sistema di raccomandazione personalizzato
- [ ] Integrazione Facebook Events
- [ ] Pagamento biglietti in-app

### P3 - Nice to Have
- [ ] Sistema follow/amici
- [ ] Notifiche social (amici partecipano)
- [ ] Dark mode
- [ ] PWA support

## Next Tasks
1. Integrare Eventbrite API per eventi reali
2. Aggiungere push notifications
3. Implementare ricerca testuale
4. Migliorare UX mobile (filtri più accessibili)

## Metrics to Track
- Tempo dal lancio all'evento scelto: target < 30 secondi
- Tap per vedere mappa: target 1
- Retention D7: target > 30%
