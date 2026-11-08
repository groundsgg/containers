<#macro content>
    <#-- Same visibility and restart URL as Keycloak's native identity control. -->
    <#if auth?has_content && auth.showUsername() && !auth.showResetCredentials()>
        <div class="grounds-restart">
            <a id="grounds-restart-login" class="btn ds-button-text" href="${url.loginRestartFlowUrl}">${msg("restartLoginTooltip")}</a>
        </div>
    </#if>
    <nav class="grounds-footer" aria-label="${msg('groundsFooterLabel')}">
        <a href="https://grounds.gg/privacy" target="_blank" rel="noopener noreferrer">${msg("groundsPrivacy")}</a>
        <span aria-hidden="true">&middot;</span>
        <a href="https://grounds.gg/imprint" target="_blank" rel="noopener noreferrer">${msg("groundsImprint")}</a>
    </nav>
</#macro>
