// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.niagara.analytics

// No method targets: the patch only wires the shared NiagaraSetup calls via
// appEntry, so there is nothing to fingerprint. The bridge lives in
// top.madkarma.patches.niagara.NiagaraSetup alongside the intro patch's
// entry points.
