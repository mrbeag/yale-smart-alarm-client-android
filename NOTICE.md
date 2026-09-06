# Notices

Yale Smart Alarm Client for Android is unofficial and is not affiliated with or endorsed by Yale or ASSA ABLOY, or presented as an official release of the upstream project.

The Yale API integration is adapted into Kotlin from [domwillcode/yale-smart-alarm-client](https://github.com/domwillcode/yale-smart-alarm-client), licensed under Apache License 2.0. Credit belongs to domwillcode and the upstream contributors for the original Python implementation.

Our adaptations include Kotlin HTTP authentication and alarm-mode requests, Android Keystore session storage, and phone, Wear OS and Android Auto interfaces. These changes are maintained separately from upstream.

This project, including its separately authored code and adaptations, is licensed under the Apache License, Version 2.0; see [LICENSE](LICENSE). Upstream attribution is retained above, and a copy of the upstream licence is also provided in [licenses/yale-smart-alarm-client-APACHE-2.0.txt](licenses/yale-smart-alarm-client-APACHE-2.0.txt). Third-party dependencies remain subject to their respective licences.

No Yale account credentials, session tokens, or upstream OAuth client values are committed to this repository. Connector-enabled binaries embed the separately supplied OAuth client value; it must not be treated as a secret protected by compilation.
