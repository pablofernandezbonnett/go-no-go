package com.pmfb.gonogo.engine.decision;

import com.pmfb.gonogo.engine.config.BlacklistedCompanyConfig;
import com.pmfb.gonogo.engine.config.CompanyConfig;
import com.pmfb.gonogo.engine.config.EngineConfig;
import com.pmfb.gonogo.engine.config.PersonaConfig;
import com.pmfb.gonogo.engine.config.RuntimeSettingsConfig;
import java.util.List;

final class DecisionTestFixtureSupport {
    private DecisionTestFixtureSupport() {
    }

    static PersonaConfig defaultPersona() {
        return new PersonaConfig(
                "product_expat_engineer",
                "Product-oriented expat engineer living in Japan",
                List.of(
                        "english_environment",
                        "product_company",
                        "engineering_culture",
                        "hybrid_work",
                        "work_life_balance",
                        "salary",
                        "stability"
                ),
                List.of("consulting_company", "onsite_only", "early_stage_startup"),
                List.of("hybrid_partial", "japanese_not_blocking", "stable_scaleup")
        );
    }

    static EngineConfig defaultConfig() {
        return new EngineConfig(
                List.of(
                        new CompanyConfig(
                                "mercari",
                                "Mercari",
                                "https://careers.mercari.com/",
                                "product",
                                "japan",
                                "Marketplace product company"
                        ),
                        new CompanyConfig(
                                "moneyforward",
                                "Money Forward",
                                "https://corp.moneyforward.com/recruit/",
                                "fintech_product",
                                "japan",
                                "Strong engineering culture"
                        ),
                        new CompanyConfig(
                                "rakuten",
                                "Rakuten",
                                "https://corp.rakuten.co.jp/careers/",
                                "product_enterprise",
                                "japan",
                                "Large enterprise product ecosystem"
                        ),
                        new CompanyConfig(
                                "smartnews",
                                "SmartNews",
                                "https://about.smartnews.com/careers/",
                                "product_scaleup",
                                "japan",
                                "International product company"
                        ),
                        new CompanyConfig(
                                "freee",
                                "freee",
                                "https://jobs.freee.co.jp/",
                                "saas_product",
                                "japan",
                                "SaaS product company"
                        )
                ),
                List.of(defaultPersona()),
                List.of(
                        new BlacklistedCompanyConfig(
                                "Randstad",
                                "Recruitment / dispatch company"
                        )
                ),
                List.of(),
                RuntimeSettingsConfig.defaults()
        );
    }
}
