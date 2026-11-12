// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "file_test.h"
#include <cstdio>
// ReSharper restore CppUnusedIncludeDirective

int main(const int count, const char *const *const arguments) {
    assert(count == 3);
    signature::tests::file_redirect_test test;
    test.prepare_files(arguments[1], arguments[2]);
    test.capture_metadata();
    test.install_redirect();
    const int redirected = test.check_contents();
    test.check_metadata(redirected);
    test.check_forked_child_filter();
    test.clean_up();
    puts("Native file redirection checks passed");
}
