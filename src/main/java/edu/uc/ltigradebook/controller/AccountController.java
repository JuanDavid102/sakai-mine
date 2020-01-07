package edu.uc.ltigradebook.controller;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import edu.uc.ltigradebook.entity.AccountPreference;
import edu.uc.ltigradebook.service.AccountService;
import edu.uc.ltigradebook.service.CanvasAPIServiceWrapper;
import edu.uc.ltigradebook.util.DateUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class AccountController {

    @Autowired
    CanvasAPIServiceWrapper canvasService;
    
    @Autowired
    AccountService accountService;

    @PostMapping(value = "/selectAccount")
    public ModelAndView selectSubaccount(@RequestParam("selectedAccount") String selectedAccount, Model model)  {
        log.info("Banner preferences, selected the account {}.", selectedAccount);
        try {
            model.addAttribute("accountList", canvasService.getSubaccounts());
            new Long(selectedAccount);
        } catch(Exception ex) {
            return new ModelAndView("admin_banner");
        }
        AccountPreference accountPreference = null;
        Optional<AccountPreference> optionalAccountPreference = accountService.getAccountPreferences(new Long(selectedAccount));
        if (optionalAccountPreference.isPresent()) {
            accountPreference = optionalAccountPreference.get();
            if(accountPreference.getBannerFromDate() != null) {
                accountPreference.setBannerFromStringDate(DateUtils.convertInstantToString(accountPreference.getBannerFromDate()));
            }
            if(accountPreference.getBannerUntilDate() != null) {
                accountPreference.setBannerUntilStringDate(DateUtils.convertInstantToString(accountPreference.getBannerUntilDate()));
            }
        } else {
            accountPreference = new AccountPreference();
            accountPreference.setAccountId(new Long(selectedAccount));
            accountPreference.setBannerEnabled(false);
            accountService.saveAccountPreferences(accountPreference);
        }
        model.addAttribute("selectedAccount", selectedAccount);
        model.addAttribute("selectedIntegerAccount", Integer.valueOf(selectedAccount));      
        model.addAttribute("accountPreference", accountPreference);
        model.addAttribute("adminBanner", true);
        return new ModelAndView("admin_banner");
    }

    @PostMapping(value = "/saveAccountPreferences")
    public ModelAndView saveAccountPreferences(AccountPreference accountPreference, final BindingResult bindingResult, Model model)  {
        try {
            if(StringUtils.isNotBlank(accountPreference.getBannerFromStringDate())) {
                accountPreference.setBannerFromDate(DateUtils.convertDateToInstant(accountPreference.getBannerFromStringDate()));
            }
            if(StringUtils.isNotBlank(accountPreference.getBannerUntilStringDate())) {
                accountPreference.setBannerUntilDate(DateUtils.convertDateToInstant(accountPreference.getBannerUntilStringDate()));
            }
            if(accountPreference.getBannerFromDate() != null && accountPreference.getBannerUntilDate() != null && accountPreference.getBannerFromDate().isAfter(accountPreference.getBannerUntilDate())) {
                throw new Exception();
            }
            log.info("Saving banner preferences {}.", accountPreference);
            accountService.saveAccountPreferences(accountPreference);
            model.addAttribute("successSavingPreferences", true);
        } catch (Exception ex) {
            model.addAttribute("errorSavingPreferences", true);            
        }
        String selectedAccount = String.valueOf(accountPreference.getAccountId());
        model.addAttribute("selectedAccount", selectedAccount);
        model.addAttribute("selectedIntegerAccount", Integer.valueOf(selectedAccount));
        try {
            model.addAttribute("accountList", canvasService.getSubaccounts());
        } catch(Exception ex) {
        }
        model.addAttribute("adminBanner", true);
        return new ModelAndView("admin_banner");
    }

}
