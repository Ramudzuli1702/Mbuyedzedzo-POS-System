package com.pos;

/**
 * The end-user licence agreement text shown by {@link com.pos.views.TermsView}
 * on first launch. Kept separate from {@link Branding} because it's a slab of
 * legal copy, not an identity constant.
 */
public final class Terms {

    private Terms() {}

    public static String text() {
        return """
                END-USER LICENCE AGREEMENT

                Please read these terms carefully before using %s. By clicking
                "Accept & Continue" below, you agree to be bound by them. If you
                do not agree, click "Decline & Exit" — the application will close
                and you should uninstall the software.

                1. LICENCE GRANT
                Subject to a valid licence key and continued compliance with
                these terms, you are granted a non-exclusive, non-transferable
                licence to install and use %s on the number of devices your
                licence permits, for your own business operations.

                2. OWNERSHIP
                The software, including its source code, design, and the
                Mbuyedzedzo name and logo, remains the property of the licensor.
                This agreement licenses its use — it does not transfer ownership.

                3. RESTRICTIONS
                You may not:
                  • Reverse-engineer, decompile, or disassemble the software,
                    except where applicable law expressly permits it;
                  • Resell, sublicense, rent, or lease the software to a third
                    party without written permission;
                  • Remove or alter any branding, licence checks, or copyright
                    notices embedded in the software.

                4. DATA AND PRIVACY
                %s stores your sales, product, staff and customer data locally
                in a MySQL database on your own machine or network — it is not
                transmitted to the vendor. Licence activation and renewal checks
                contact the licensing server only to validate your licence key.
                If you enable optional features such as e-mailed receipts, the
                mail account you configure is used solely to send those
                messages on your behalf.

                5. UPDATES
                Updates and patches may be provided at the vendor's discretion
                and may themselves be subject to additional or amended terms.

                6. WARRANTY DISCLAIMER
                The software is provided "as is", without warranty of any kind,
                express or implied, including but not limited to warranties of
                merchantability or fitness for a particular purpose. You are
                responsible for keeping independent backups of your data.

                7. LIMITATION OF LIABILITY
                To the maximum extent permitted by law, the licensor is not
                liable for any indirect, incidental, or consequential damages —
                including lost profits or lost data — arising from use of the
                software.

                8. TERMINATION
                This licence terminates automatically if you breach these
                terms. On termination you must stop using the software.

                9. GOVERNING LAW
                These terms are governed by the laws of the Republic of South
                Africa.

                10. CONTACT
                Questions about this agreement can be directed to the vendor
                through the support channel provided with your licence.

                By clicking "Accept & Continue" you confirm that you have read,
                understood, and agree to these terms.
                """.formatted(Branding.APP_NAME, Branding.APP_NAME, Branding.APP_NAME);
    }
}
